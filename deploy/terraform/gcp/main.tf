# ─────────────────────────────────────────────────────────────────────────────
# GIMI CI/CD — GCP Deployment (Cloud Run + Cloud SQL + Memorystore)
# ─────────────────────────────────────────────────────────────────────────────

terraform {
  required_version = ">= 1.5"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project
  region  = var.region
}

variable "project"        { type = string }
variable "region"         { default = "us-central1" }
variable "domain"         { default = "gimi.example.com" }
variable "admin_password" { sensitive = true }
variable "jwt_secret"     { sensitive = true }
variable "worker_count"   { default = 3 }
variable "environment"    { default = "production" }

locals {
  name = "gimi-${var.environment}"
}

# ── VPC ──────────────────────────────────────────────────────────────────────
resource "google_compute_network" "gimi" {
  name                    = local.name
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "gimi" {
  name          = local.name
  ip_cidr_range = "10.0.0.0/20"
  region        = var.region
  network       = google_compute_network.gimi.id

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.1.0.0/20"
  }
}

# ── Cloud SQL PostgreSQL ─────────────────────────────────────────────────────
resource "google_sql_database_instance" "gimi" {
  name             = local.name
  database_version = "POSTGRES_16"
  region           = var.region

  settings {
    tier              = "db-custom-2-4096"
    availability_type = var.environment == "production" ? "REGIONAL" : "ZONAL"

    ip_configuration {
      ipv4_enabled    = false
      private_network = google_compute_network.gimi.id
    }

    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = var.environment == "production"
    }
  }

  deletion_protection = var.environment == "production"
}

resource "google_sql_database" "gimi" {
  name     = "gimi"
  instance = google_sql_database_instance.gimi.name
}

resource "google_sql_user" "gimi" {
  name     = "gimi"
  instance = google_sql_database_instance.gimi.name
  password = var.admin_password
}

# ── Memorystore Redis ────────────────────────────────────────────────────────
resource "google_redis_instance" "gimi" {
  name           = local.name
  tier           = var.environment == "production" ? "STANDARD_HA" : "BASIC"
  memory_size_gb = 2
  region         = var.region

  authorized_network = google_compute_network.gimi.id
  redis_version      = "REDIS_7_0"
  display_name       = "GIMI CI/CD Redis"

  transit_encryption_mode = "SERVER_AUTHENTICATION"
}

# ── Artifact Registry ────────────────────────────────────────────────────────
resource "google_artifact_registry_repository" "gimi" {
  location      = var.region
  repository_id = local.name
  format        = "DOCKER"
}

# ── Cloud Run — Server ───────────────────────────────────────────────────────
resource "google_cloud_run_v2_service" "server" {
  name     = "${local.name}-server"
  location = var.region

  template {
    scaling {
      min_instance_count = 1
      max_instance_count = 10
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project}/${local.name}/gimi-server:latest"

      ports {
        container_port = 8080
      }

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod,distributed"
      }
      env {
        name  = "GIMI_DB_URL"
        value = "jdbc:postgresql://${google_sql_database_instance.gimi.private_ip_address}:5432/gimi"
      }
      env {
        name  = "GIMI_DB_USERNAME"
        value = "gimi"
      }
      env {
        name = "GIMI_DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "GIMI_REDIS_URL"
        value = "redis://${google_redis_instance.gimi.host}:${google_redis_instance.gimi.port}"
      }
      env {
        name = "GIMI_JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.jwt_secret.secret_id
            version = "latest"
          }
        }
      }

      resources {
        limits = {
          cpu    = "2"
          memory = "2Gi"
        }
      }

      startup_probe {
        http_get {
          path = "/actuator/health"
          port = 8080
        }
        initial_delay_seconds = 10
        period_seconds        = 5
        failure_threshold     = 30
      }

      liveness_probe {
        http_get {
          path = "/actuator/health"
          port = 8080
        }
        period_seconds = 15
      }
    }

    vpc_access {
      network_interfaces {
        network    = google_compute_network.gimi.name
        subnetwork = google_compute_subnetwork.gimi.name
      }
    }
  }
}

# ── Cloud Run — Worker ───────────────────────────────────────────────────────
resource "google_cloud_run_v2_service" "worker" {
  name     = "${local.name}-worker"
  location = var.region

  template {
    scaling {
      min_instance_count = var.worker_count
      max_instance_count = 50
    }

    containers {
      image = "${var.region}-docker.pkg.dev/${var.project}/${local.name}/gimi-worker:latest"

      env {
        name  = "GIMI_REDIS_URL"
        value = "redis://${google_redis_instance.gimi.host}:${google_redis_instance.gimi.port}"
      }
      env {
        name  = "GIMI_SERVER_URL"
        value = google_cloud_run_v2_service.server.uri
      }
      env {
        name  = "GIMI_WORKER_MAX_CONCURRENT"
        value = "200"
      }

      resources {
        limits = {
          cpu    = "4"
          memory = "4Gi"
        }
      }
    }

    vpc_access {
      network_interfaces {
        network    = google_compute_network.gimi.name
        subnetwork = google_compute_subnetwork.gimi.name
      }
    }
  }
}

# ── Secret Manager ───────────────────────────────────────────────────────────
resource "google_secret_manager_secret" "db_password" {
  secret_id = "${local.name}-db-password"
  replication { auto {} }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = var.admin_password
}

resource "google_secret_manager_secret" "jwt_secret" {
  secret_id = "${local.name}-jwt-secret"
  replication { auto {} }
}

resource "google_secret_manager_secret_version" "jwt_secret" {
  secret      = google_secret_manager_secret.jwt_secret.id
  secret_data = var.jwt_secret
}

# ── Outputs ──────────────────────────────────────────────────────────────────
output "server_url" {
  value = google_cloud_run_v2_service.server.uri
}

output "worker_url" {
  value = google_cloud_run_v2_service.worker.uri
}

output "database_ip" {
  value = google_sql_database_instance.gimi.private_ip_address
}

output "redis_host" {
  value = google_redis_instance.gimi.host
}
