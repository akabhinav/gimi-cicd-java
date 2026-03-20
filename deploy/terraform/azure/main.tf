# ─────────────────────────────────────────────────────────────────────────────
# GIMI CI/CD — Azure Deployment (Container Apps + PostgreSQL + Redis)
# ─────────────────────────────────────────────────────────────────────────────

terraform {
  required_version = ">= 1.5"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
    }
  }
}

provider "azurerm" {
  features {}
}

variable "location"       { default = "eastus" }
variable "domain"         { default = "gimi.example.com" }
variable "admin_password" { sensitive = true }
variable "jwt_secret"     { sensitive = true }
variable "worker_count"   { default = 3 }
variable "environment"    { default = "production" }

locals {
  name = "gimi-${var.environment}"
  tags = {
    Project     = "gimi-cicd"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# ── Resource Group ───────────────────────────────────────────────────────────
resource "azurerm_resource_group" "gimi" {
  name     = local.name
  location = var.location
  tags     = local.tags
}

# ── Virtual Network ──────────────────────────────────────────────────────────
resource "azurerm_virtual_network" "gimi" {
  name                = local.name
  resource_group_name = azurerm_resource_group.gimi.name
  location            = azurerm_resource_group.gimi.location
  address_space       = ["10.0.0.0/16"]
  tags                = local.tags
}

resource "azurerm_subnet" "app" {
  name                 = "${local.name}-app"
  resource_group_name  = azurerm_resource_group.gimi.name
  virtual_network_name = azurerm_virtual_network.gimi.name
  address_prefixes     = ["10.0.1.0/24"]

  delegation {
    name = "container-apps"
    service_delegation {
      name = "Microsoft.App/environments"
      actions = [
        "Microsoft.Network/virtualNetworks/subnets/join/action",
      ]
    }
  }
}

resource "azurerm_subnet" "db" {
  name                 = "${local.name}-db"
  resource_group_name  = azurerm_resource_group.gimi.name
  virtual_network_name = azurerm_virtual_network.gimi.name
  address_prefixes     = ["10.0.2.0/24"]

  delegation {
    name = "postgres"
    service_delegation {
      name = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = [
        "Microsoft.Network/virtualNetworks/subnets/join/action",
      ]
    }
  }
}

# ── PostgreSQL Flexible Server ───────────────────────────────────────────────
resource "azurerm_postgresql_flexible_server" "gimi" {
  name                   = local.name
  resource_group_name    = azurerm_resource_group.gimi.name
  location               = azurerm_resource_group.gimi.location
  version                = "16"
  delegated_subnet_id    = azurerm_subnet.db.id
  administrator_login    = "gimi"
  administrator_password = var.admin_password

  sku_name   = var.environment == "production" ? "GP_Standard_D2s_v3" : "B_Standard_B1ms"
  storage_mb = 32768

  high_availability {
    mode = var.environment == "production" ? "ZoneRedundant" : "Disabled"
  }

  tags = local.tags
}

resource "azurerm_postgresql_flexible_server_database" "gimi" {
  name      = "gimi"
  server_id = azurerm_postgresql_flexible_server.gimi.id
}

# ── Azure Cache for Redis ────────────────────────────────────────────────────
resource "azurerm_redis_cache" "gimi" {
  name                = local.name
  resource_group_name = azurerm_resource_group.gimi.name
  location            = azurerm_resource_group.gimi.location
  capacity            = 1
  family              = "C"
  sku_name            = var.environment == "production" ? "Standard" : "Basic"
  minimum_tls_version = "1.2"

  redis_configuration {
    maxmemory_policy = "allkeys-lru"
  }

  tags = local.tags
}

# ── Container App Environment ────────────────────────────────────────────────
resource "azurerm_log_analytics_workspace" "gimi" {
  name                = local.name
  resource_group_name = azurerm_resource_group.gimi.name
  location            = azurerm_resource_group.gimi.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_container_app_environment" "gimi" {
  name                       = local.name
  resource_group_name        = azurerm_resource_group.gimi.name
  location                   = azurerm_resource_group.gimi.location
  log_analytics_workspace_id = azurerm_log_analytics_workspace.gimi.id
  infrastructure_subnet_id   = azurerm_subnet.app.id
  tags                       = local.tags
}

# ── Container App — Server ───────────────────────────────────────────────────
resource "azurerm_container_app" "server" {
  name                         = "${local.name}-server"
  container_app_environment_id = azurerm_container_app_environment.gimi.id
  resource_group_name          = azurerm_resource_group.gimi.name
  revision_mode                = "Single"

  template {
    min_replicas = 2
    max_replicas = 10

    container {
      name   = "gimi-server"
      image  = "gimi/gimi-server:latest"
      cpu    = 1.0
      memory = "2Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod,distributed"
      }
      env {
        name  = "GIMI_DB_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.gimi.fqdn}:5432/gimi?sslmode=require"
      }
      env {
        name  = "GIMI_DB_USERNAME"
        value = "gimi"
      }
      env {
        name        = "GIMI_DB_PASSWORD"
        secret_name = "db-password"
      }
      env {
        name  = "GIMI_REDIS_URL"
        value = "rediss://:${azurerm_redis_cache.gimi.primary_access_key}@${azurerm_redis_cache.gimi.hostname}:6380"
      }
      env {
        name        = "GIMI_JWT_SECRET"
        secret_name = "jwt-secret"
      }
      env {
        name        = "GIMI_ADMIN_PASSWORD"
        secret_name = "admin-password"
      }

      liveness_probe {
        transport = "HTTP"
        path      = "/actuator/health"
        port      = 8080
      }

      readiness_probe {
        transport = "HTTP"
        path      = "/actuator/health"
        port      = 8080
      }
    }
  }

  secret {
    name  = "db-password"
    value = var.admin_password
  }
  secret {
    name  = "jwt-secret"
    value = var.jwt_secret
  }
  secret {
    name  = "admin-password"
    value = var.admin_password
  }

  ingress {
    external_enabled = true
    target_port      = 8080
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }

  tags = local.tags
}

# ── Container App — Worker ───────────────────────────────────────────────────
resource "azurerm_container_app" "worker" {
  name                         = "${local.name}-worker"
  container_app_environment_id = azurerm_container_app_environment.gimi.id
  resource_group_name          = azurerm_resource_group.gimi.name
  revision_mode                = "Single"

  template {
    min_replicas = var.worker_count
    max_replicas = 50

    container {
      name   = "gimi-worker"
      image  = "gimi/gimi-worker:latest"
      cpu    = 2.0
      memory = "4Gi"

      env {
        name  = "GIMI_REDIS_URL"
        value = "rediss://:${azurerm_redis_cache.gimi.primary_access_key}@${azurerm_redis_cache.gimi.hostname}:6380"
      }
      env {
        name  = "GIMI_SERVER_URL"
        value = "https://${azurerm_container_app.server.ingress[0].fqdn}"
      }
      env {
        name  = "GIMI_WORKER_MAX_CONCURRENT"
        value = "200"
      }
    }
  }

  tags = local.tags
}

# ── Outputs ──────────────────────────────────────────────────────────────────
output "server_url" {
  value = "https://${azurerm_container_app.server.ingress[0].fqdn}"
}

output "database_fqdn" {
  value = azurerm_postgresql_flexible_server.gimi.fqdn
}

output "redis_hostname" {
  value = azurerm_redis_cache.gimi.hostname
}

output "resource_group" {
  value = azurerm_resource_group.gimi.name
}
