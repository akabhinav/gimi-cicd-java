# GIMI CI/CD — Deployment Guide

Deploy GIMI to any cloud in minutes.

## Quick Start

```bash
# Local (Docker) — fastest way to try
./deploy/deploy.sh local

# Kubernetes — any cluster (EKS, GKE, AKS, k3s, kind)
./deploy/deploy.sh kubernetes

# Helm — production Kubernetes
./deploy/deploy.sh helm

# Cloud-specific
./deploy/deploy.sh aws     # ECS Fargate + RDS + ElastiCache
./deploy/deploy.sh gcp     # Cloud Run + Cloud SQL + Memorystore
./deploy/deploy.sh azure   # Container Apps + PostgreSQL + Redis
```

## Deployment Options

| Mode | Best For | Infra Provisioned | Time |
|------|----------|-------------------|------|
| `local` | Development, testing | Docker Compose (local) | ~2 min |
| `docker` | Single VM, staging | Docker Compose (prod settings) | ~3 min |
| `kubernetes` | Any K8s cluster | kubectl apply (kustomize) | ~5 min |
| `helm` | Production K8s | Helm chart with values | ~5 min |
| `aws` | AWS production | VPC, ECS, RDS, ElastiCache, ALB | ~15 min |
| `gcp` | GCP production | VPC, Cloud Run, Cloud SQL, Memorystore | ~12 min |
| `azure` | Azure production | VNet, Container Apps, PostgreSQL, Redis | ~12 min |

## Configuration

Set environment variables before running `deploy.sh`:

```bash
export GIMI_DOMAIN=ci.mycompany.com
export GIMI_ADMIN_PASSWORD=MySecurePass123
export GIMI_JWT_SECRET=$(openssl rand -base64 32)
export GIMI_WORKERS=5
```

| Variable | Default | Description |
|----------|---------|-------------|
| `GIMI_DOMAIN` | `gimi.example.com` | Domain for ingress/load balancer |
| `GIMI_ADMIN_PASSWORD` | auto-generated | Admin user password |
| `GIMI_JWT_SECRET` | auto-generated | JWT signing secret |
| `GIMI_WORKERS` | `3` | Number of worker instances |
| `GIMI_NAMESPACE` | `gimi` | Kubernetes namespace |
| `GIMI_DB_URL` | auto-provisioned | External PostgreSQL URL |
| `GIMI_REDIS_URL` | auto-provisioned | External Redis URL |

## Helm Chart

### Install

```bash
helm upgrade --install gimi ./deploy/helm/gimi \
  --namespace gimi --create-namespace \
  --set ingress.host=ci.mycompany.com \
  --set secrets.adminPassword=MyPassword \
  --set worker.replicas=5
```

### Use External Database

```bash
helm upgrade --install gimi ./deploy/helm/gimi \
  --set postgresql.enabled=false \
  --set postgresql.external.url="jdbc:postgresql://mydb.example.com:5432/gimi" \
  --set postgresql.external.username=gimi \
  --set postgresql.external.password=dbpass \
  --set redis.enabled=false \
  --set redis.external.url="redis://myredis.example.com:6379"
```

### Custom Values File

```yaml
# my-values.yaml
server:
  replicas: 3
  resources:
    limits:
      cpu: "4"
      memory: 4Gi

worker:
  replicas: 10
  autoscaling:
    maxReplicas: 100

ingress:
  host: ci.mycompany.com
  tls:
    enabled: true

postgresql:
  enabled: false
  external:
    url: "jdbc:postgresql://prod-db:5432/gimi"
```

```bash
helm upgrade --install gimi ./deploy/helm/gimi -f my-values.yaml
```

## Terraform (Cloud-Specific)

### AWS

```bash
cd deploy/terraform/aws
terraform init
terraform apply \
  -var="region=us-west-2" \
  -var="domain=ci.mycompany.com" \
  -var="admin_password=MyPassword" \
  -var="jwt_secret=MyJwtSecret" \
  -var="worker_count=5"
```

Provisions: VPC, Aurora PostgreSQL Serverless v2, ElastiCache Redis, ECS Fargate, ALB, Auto Scaling.

### GCP

```bash
cd deploy/terraform/gcp
terraform init
terraform apply \
  -var="project=my-gcp-project" \
  -var="region=us-central1" \
  -var="admin_password=MyPassword" \
  -var="jwt_secret=MyJwtSecret"
```

Provisions: VPC, Cloud SQL PostgreSQL 16, Memorystore Redis 7, Cloud Run (server + worker), Artifact Registry.

### Azure

```bash
cd deploy/terraform/azure
terraform init
terraform apply \
  -var="location=eastus" \
  -var="admin_password=MyPassword" \
  -var="jwt_secret=MyJwtSecret"
```

Provisions: VNet, PostgreSQL Flexible Server, Azure Cache for Redis, Container Apps, Log Analytics.

## Architecture

```
                    ┌──────────────┐
                    │  Load Balancer│
                    │  (ALB/Ingress)│
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────┴─────┐ ┌───┴───┐ ┌─────┴─────┐
        │ GIMI UI   │ │Server │ │  Server   │
        │ (React)   │ │  (1)  │ │   (2)     │
        └───────────┘ └───┬───┘ └─────┬─────┘
                          │           │
                    ┌─────┴───────────┴─────┐
                    │        Redis          │
                    │    (Job Queue)        │
                    └─────────┬─────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
        ┌─────┴────┐  ┌──────┴───┐  ┌───────┴──┐
        │ Worker 1 │  │ Worker 2 │  │ Worker N │
        └──────────┘  └──────────┘  └──────────┘
                              │
                    ┌─────────┴─────────┐
                    │    PostgreSQL      │
                    │  (Execution Store) │
                    └───────────────────┘
```

## Scaling

```bash
# Docker Compose
docker compose up -d --scale gimi-worker=20

# Kubernetes
kubectl scale deploy/gimi-worker --replicas=20 -n gimi

# Helm
helm upgrade gimi ./deploy/helm/gimi --set worker.replicas=20

# Workers auto-scale based on CPU (70%) and memory (80%) utilization
```

## Prerequisites

| Mode | Requirements |
|------|-------------|
| `local` | Docker, Docker Compose |
| `kubernetes` | kubectl, connected cluster |
| `helm` | kubectl, helm 3, connected cluster |
| `aws` | AWS CLI, Terraform, AWS credentials |
| `gcp` | gcloud CLI, Terraform, GCP project |
| `azure` | Azure CLI, Terraform, Azure subscription |
