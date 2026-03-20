#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# GIMI CI/CD — Universal One-Click Deploy Script
# Deploys to any environment: local Docker, Kubernetes, AWS, GCP, Azure
# ─────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Defaults
DEPLOY_MODE="${1:-}"
NAMESPACE="${GIMI_NAMESPACE:-gimi}"
DOMAIN="${GIMI_DOMAIN:-gimi.example.com}"
ADMIN_PASSWORD="${GIMI_ADMIN_PASSWORD:-$(openssl rand -base64 16 2>/dev/null || echo 'changeme123')}"
JWT_SECRET="${GIMI_JWT_SECRET:-$(openssl rand -base64 32 2>/dev/null || echo 'dev-jwt-secret-change-in-prod')}"
WORKERS="${GIMI_WORKERS:-3}"

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

log()   { echo -e "${GREEN}[GIMI]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
header() { echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${BLUE}  $*${NC}"; echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }

usage() {
    cat <<'EOF'
GIMI CI/CD — Universal Deployment Script

Usage: ./deploy.sh <mode> [options]

Deployment Modes:
  local         Docker Compose on local machine (dev/test)
  docker        Docker Compose on remote VM (production-ready)
  kubernetes    Deploy to any Kubernetes cluster (EKS/GKE/AKS/k3s/kind)
  helm          Deploy via Helm chart to Kubernetes
  aws           Full AWS deployment (ECS Fargate + RDS + ElastiCache)
  gcp           Full GCP deployment (Cloud Run + Cloud SQL + Memorystore)
  azure         Full Azure deployment (Container Apps + PostgreSQL + Redis)

Quick Start:
  ./deploy.sh local                    # Fastest: runs locally with Docker
  ./deploy.sh kubernetes               # Deploy to current kubectl context
  ./deploy.sh helm --set workers=5     # Helm-based deploy with 5 workers

Environment Variables:
  GIMI_NAMESPACE        Kubernetes namespace (default: gimi)
  GIMI_DOMAIN           Domain for ingress (default: gimi.example.com)
  GIMI_ADMIN_PASSWORD   Admin password (auto-generated if not set)
  GIMI_JWT_SECRET       JWT signing secret (auto-generated if not set)
  GIMI_WORKERS          Number of workers (default: 3)
  GIMI_DB_URL           PostgreSQL URL (auto-provisioned if not set)
  GIMI_REDIS_URL        Redis URL (auto-provisioned if not set)

Examples:
  GIMI_WORKERS=5 ./deploy.sh local
  GIMI_DOMAIN=ci.mycompany.com ./deploy.sh kubernetes
  ./deploy.sh aws --region us-west-2
  ./deploy.sh helm --values my-values.yaml
EOF
    exit 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Prerequisites Check
# ─────────────────────────────────────────────────────────────────────────────
check_command() {
    command -v "$1" &>/dev/null || { error "$1 is required but not installed. Install it first."; return 1; }
}

check_prerequisites_docker() {
    check_command docker
    check_command docker-compose || check_command "docker compose"
    docker info &>/dev/null || { error "Docker daemon is not running. Start Docker first."; exit 1; }
}

check_prerequisites_k8s() {
    check_command kubectl
    kubectl cluster-info &>/dev/null || { error "No Kubernetes cluster connected. Configure kubectl first."; exit 1; }
}

check_prerequisites_helm() {
    check_prerequisites_k8s
    check_command helm
}

# ─────────────────────────────────────────────────────────────────────────────
# Deploy: Local (Docker Compose)
# ─────────────────────────────────────────────────────────────────────────────
deploy_local() {
    header "Deploying GIMI CI/CD — Local Docker Compose"
    check_prerequisites_docker

    cd "$ROOT_DIR"
    log "Building images..."
    docker compose build

    log "Starting services (server + $WORKERS workers + UI + PostgreSQL + Redis)..."
    docker compose up -d --scale gimi-worker="$WORKERS"

    log "Waiting for services to be healthy..."
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8080/actuator/health &>/dev/null; then
            break
        fi
        sleep 2
    done

    echo ""
    log "GIMI CI/CD is running!"
    echo ""
    echo "  UI:     http://localhost:3000"
    echo "  API:    http://localhost:8080"
    echo "  Health: http://localhost:8080/actuator/health"
    echo ""
    echo "  Admin password: $ADMIN_PASSWORD"
    echo ""
    echo "  Stop:   docker compose down"
    echo "  Logs:   docker compose logs -f"
    echo "  Scale:  docker compose up -d --scale gimi-worker=10"
}

# ─────────────────────────────────────────────────────────────────────────────
# Deploy: Docker (Production on VM)
# ─────────────────────────────────────────────────────────────────────────────
deploy_docker() {
    header "Deploying GIMI CI/CD — Production Docker Compose"
    check_prerequisites_docker

    cd "$ROOT_DIR"
    log "Building optimized production images..."
    docker compose build

    log "Starting with production settings..."
    GIMI_PROFILE=prod \
    GIMI_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    GIMI_JWT_SECRET="$JWT_SECRET" \
    docker compose up -d --scale gimi-worker="$WORKERS"

    log "GIMI CI/CD deployed in production Docker mode."
    echo "  Workers: $WORKERS"
    echo "  Admin password: $ADMIN_PASSWORD"
}

# ─────────────────────────────────────────────────────────────────────────────
# Deploy: Kubernetes (kubectl apply)
# ─────────────────────────────────────────────────────────────────────────────
deploy_kubernetes() {
    header "Deploying GIMI CI/CD — Kubernetes"
    check_prerequisites_k8s

    local context
    context=$(kubectl config current-context)
    log "Deploying to cluster: $context"
    log "Namespace: $NAMESPACE"

    # Create namespace
    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

    # Generate secrets
    log "Configuring secrets..."
    kubectl create secret generic gimi-secrets \
        --namespace "$NAMESPACE" \
        --from-literal=postgres-url="jdbc:postgresql://postgres:5432/gimi" \
        --from-literal=postgres-username=gimi \
        --from-literal=postgres-password="$(openssl rand -base64 16)" \
        --from-literal=jwt-secret="$JWT_SECRET" \
        --from-literal=webhook-secret="$(openssl rand -base64 16)" \
        --from-literal=redis-url="redis://redis:6379" \
        --from-literal=admin-password="$ADMIN_PASSWORD" \
        --from-literal=worker-token="$(openssl rand -base64 24)" \
        --dry-run=client -o yaml | kubectl apply -f -

    # Apply kustomize manifests
    log "Applying Kubernetes manifests..."
    kubectl apply -k "$ROOT_DIR/k8s/base/" --namespace "$NAMESPACE"

    # Wait for rollout
    log "Waiting for deployments..."
    kubectl rollout status deployment/gimi-server -n "$NAMESPACE" --timeout=180s
    kubectl rollout status deployment/gimi-worker -n "$NAMESPACE" --timeout=180s

    echo ""
    log "GIMI CI/CD deployed to Kubernetes!"
    echo ""
    echo "  Namespace: $NAMESPACE"
    echo "  Context:   $context"
    echo ""
    echo "  Port-forward: kubectl port-forward svc/gimi-server 8080:8080 -n $NAMESPACE"
    echo "  Logs:         kubectl logs -f deploy/gimi-server -n $NAMESPACE"
    echo "  Scale:        kubectl scale deploy/gimi-worker --replicas=10 -n $NAMESPACE"
}

# ─────────────────────────────────────────────────────────────────────────────
# Deploy: Helm
# ─────────────────────────────────────────────────────────────────────────────
deploy_helm() {
    header "Deploying GIMI CI/CD — Helm Chart"
    check_prerequisites_helm

    shift || true  # consume 'helm' arg
    local helm_args=("$@")

    log "Installing/upgrading GIMI Helm chart..."
    helm upgrade --install gimi "$ROOT_DIR/deploy/helm/gimi" \
        --namespace "$NAMESPACE" --create-namespace \
        --set server.replicas=2 \
        --set worker.replicas="$WORKERS" \
        --set ingress.host="$DOMAIN" \
        --set secrets.jwtSecret="$JWT_SECRET" \
        --set secrets.adminPassword="$ADMIN_PASSWORD" \
        "${helm_args[@]}" \
        --wait --timeout 5m

    echo ""
    log "GIMI CI/CD deployed via Helm!"
    echo ""
    echo "  Release:   gimi"
    echo "  Namespace: $NAMESPACE"
    echo "  Domain:    $DOMAIN"
    echo ""
    echo "  Status:    helm status gimi -n $NAMESPACE"
    echo "  Upgrade:   helm upgrade gimi $ROOT_DIR/deploy/helm/gimi -n $NAMESPACE"
    echo "  Rollback:  helm rollback gimi -n $NAMESPACE"
    echo "  Uninstall: helm uninstall gimi -n $NAMESPACE"
}

# ─────────────────────────────────────────────────────────────────────────────
# Deploy: AWS (ECS Fargate + RDS + ElastiCache)
# ─────────────────────────────────────────────────────────────────────────────
deploy_aws() {
    header "Deploying GIMI CI/CD — AWS"
    check_command aws
    check_command terraform

    local region="${GIMI_AWS_REGION:-us-east-1}"
    log "Region: $region"

    cd "$ROOT_DIR/deploy/terraform/aws"

    log "Initializing Terraform..."
    terraform init

    log "Planning infrastructure..."
    terraform plan \
        -var="region=$region" \
        -var="domain=$DOMAIN" \
        -var="admin_password=$ADMIN_PASSWORD" \
        -var="jwt_secret=$JWT_SECRET" \
        -var="worker_count=$WORKERS" \
        -out=tfplan

    log "Apply the plan? (terraform will prompt for confirmation)"
    terraform apply tfplan

    echo ""
    log "GIMI CI/CD deployed to AWS!"
    terraform output
}

# ─────────────────────────────────────────────────────────────────────────────
# Deploy: GCP (Cloud Run + Cloud SQL + Memorystore)
# ─────────────────────────────────────────────────────────────────────────────
deploy_gcp() {
    header "Deploying GIMI CI/CD — Google Cloud"
    check_command gcloud
    check_command terraform

    local project="${GIMI_GCP_PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
    local region="${GIMI_GCP_REGION:-us-central1}"
    log "Project: $project, Region: $region"

    cd "$ROOT_DIR/deploy/terraform/gcp"

    terraform init
    terraform plan \
        -var="project=$project" \
        -var="region=$region" \
        -var="domain=$DOMAIN" \
        -var="admin_password=$ADMIN_PASSWORD" \
        -var="jwt_secret=$JWT_SECRET" \
        -var="worker_count=$WORKERS" \
        -out=tfplan

    terraform apply tfplan

    echo ""
    log "GIMI CI/CD deployed to GCP!"
    terraform output
}

# ─────────────────────────────────────────────────────────────────────────────
# Deploy: Azure (Container Apps + PostgreSQL + Redis)
# ─────────────────────────────────────────────────────────────────────────────
deploy_azure() {
    header "Deploying GIMI CI/CD — Azure"
    check_command az
    check_command terraform

    local region="${GIMI_AZURE_REGION:-eastus}"
    log "Region: $region"

    cd "$ROOT_DIR/deploy/terraform/azure"

    terraform init
    terraform plan \
        -var="location=$region" \
        -var="domain=$DOMAIN" \
        -var="admin_password=$ADMIN_PASSWORD" \
        -var="jwt_secret=$JWT_SECRET" \
        -var="worker_count=$WORKERS" \
        -out=tfplan

    terraform apply tfplan

    echo ""
    log "GIMI CI/CD deployed to Azure!"
    terraform output
}

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────
case "${DEPLOY_MODE}" in
    local)       deploy_local ;;
    docker)      deploy_docker ;;
    kubernetes)  deploy_kubernetes ;;
    helm)        deploy_helm "$@" ;;
    aws)         deploy_aws ;;
    gcp)         deploy_gcp ;;
    azure)       deploy_azure ;;
    -h|--help|"") usage ;;
    *)           error "Unknown mode: $DEPLOY_MODE"; usage ;;
esac
