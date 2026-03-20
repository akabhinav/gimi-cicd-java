# ─────────────────────────────────────────────────────────────────────────────
# GIMI CI/CD — Windows PowerShell One-Click Deploy Script
# Deploys to: local Docker, Kubernetes, Helm, AWS, GCP, Azure
# ─────────────────────────────────────────────────────────────────────────────

param(
    [Parameter(Position=0)]
    [ValidateSet("local", "docker", "kubernetes", "helm", "aws", "gcp", "azure", "help")]
    [string]$Mode = "help",

    [string]$Namespace = $env:GIMI_NAMESPACE ?? "gimi",
    [string]$Domain = $env:GIMI_DOMAIN ?? "gimi.example.com",
    [string]$AdminPassword = $env:GIMI_ADMIN_PASSWORD ?? (-join ((65..90)+(97..122)+(48..57) | Get-Random -Count 16 | ForEach-Object {[char]$_})),
    [string]$JwtSecret = $env:GIMI_JWT_SECRET ?? (-join ((65..90)+(97..122)+(48..57) | Get-Random -Count 32 | ForEach-Object {[char]$_})),
    [int]$Workers = $env:GIMI_WORKERS ?? 3,
    [string]$Region,
    [string]$ValuesFile
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir

# ── Helpers ──────────────────────────────────────────────────────────────────

function Write-Header($text) {
    Write-Host ""
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Blue
    Write-Host "  $text" -ForegroundColor Blue
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Blue
    Write-Host ""
}

function Write-Log($text) {
    Write-Host "[GIMI] $text" -ForegroundColor Green
}

function Write-Err($text) {
    Write-Host "[ERROR] $text" -ForegroundColor Red
}

function Test-Command($cmd) {
    return [bool](Get-Command $cmd -ErrorAction SilentlyContinue)
}

function Assert-Command($cmd, $installUrl) {
    if (-not (Test-Command $cmd)) {
        Write-Err "$cmd is required but not installed."
        Write-Host "  Install: $installUrl" -ForegroundColor Yellow
        exit 1
    }
}

function Assert-Docker {
    Assert-Command "docker" "https://www.docker.com/products/docker-desktop"
    $info = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Docker Desktop is not running. Start it first."
        exit 1
    }
}

function Assert-Kubectl {
    Assert-Command "kubectl" "https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/"
    kubectl cluster-info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Err "No Kubernetes cluster connected. Configure kubectl first."
        exit 1
    }
}

function Assert-Helm {
    Assert-Kubectl
    Assert-Command "helm" "https://helm.sh/docs/intro/install/"
}

function Assert-Terraform {
    Assert-Command "terraform" "https://developer.hashicorp.com/terraform/install"
}

# ── Deploy: Local ────────────────────────────────────────────────────────────

function Deploy-Local {
    Write-Header "Deploying GIMI CI/CD — Local Docker Compose"
    Assert-Docker

    Set-Location $RootDir

    Write-Log "Building images..."
    docker compose build
    if ($LASTEXITCODE -ne 0) { Write-Err "Build failed"; exit 1 }

    Write-Log "Starting services (server + $Workers workers + UI + PostgreSQL + Redis)..."
    docker compose up -d --scale gimi-worker=$Workers
    if ($LASTEXITCODE -ne 0) { Write-Err "Startup failed"; exit 1 }

    Write-Log "Waiting for services to be healthy..."
    $healthy = $false
    for ($i = 0; $i -lt 30; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }

    Write-Host ""
    Write-Log "GIMI CI/CD is running!"
    Write-Host ""
    Write-Host "  UI:     http://localhost:3000"
    Write-Host "  API:    http://localhost:8080"
    Write-Host "  Health: http://localhost:8080/actuator/health"
    Write-Host ""
    Write-Host "  Admin password: $AdminPassword"
    Write-Host ""
    Write-Host "  Stop:   docker compose down"
    Write-Host "  Logs:   docker compose logs -f"
    Write-Host "  Scale:  docker compose up -d --scale gimi-worker=10"
}

# ── Deploy: Docker (Production) ─────────────────────────────────────────────

function Deploy-Docker {
    Write-Header "Deploying GIMI CI/CD — Production Docker Compose"
    Assert-Docker

    Set-Location $RootDir

    Write-Log "Building optimized production images..."
    docker compose build

    Write-Log "Starting with production settings..."
    $env:GIMI_PROFILE = "prod"
    $env:GIMI_ADMIN_PASSWORD = $AdminPassword
    $env:GIMI_JWT_SECRET = $JwtSecret
    docker compose up -d --scale gimi-worker=$Workers

    Write-Host ""
    Write-Log "GIMI CI/CD deployed in production Docker mode."
    Write-Host "  Workers: $Workers"
    Write-Host "  Admin password: $AdminPassword"
}

# ── Deploy: Kubernetes ───────────────────────────────────────────────────────

function Deploy-Kubernetes {
    Write-Header "Deploying GIMI CI/CD — Kubernetes"
    Assert-Kubectl

    $context = kubectl config current-context
    Write-Log "Deploying to cluster: $context"
    Write-Log "Namespace: $Namespace"

    kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f -

    Write-Log "Configuring secrets..."
    kubectl create secret generic gimi-secrets `
        --namespace $Namespace `
        --from-literal=postgres-url="jdbc:postgresql://postgres:5432/gimi" `
        --from-literal=postgres-username=gimi `
        --from-literal=postgres-password=$AdminPassword `
        --from-literal=jwt-secret=$JwtSecret `
        --from-literal=webhook-secret="webhook-secret" `
        --from-literal=redis-url="redis://redis:6379" `
        --from-literal=admin-password=$AdminPassword `
        --from-literal=worker-token="worker-token" `
        --dry-run=client -o yaml | kubectl apply -f -

    Write-Log "Applying Kubernetes manifests..."
    kubectl apply -k "$RootDir\k8s\base\" --namespace $Namespace

    Write-Log "Waiting for deployments..."
    kubectl rollout status deployment/gimi-server -n $Namespace --timeout=180s
    kubectl rollout status deployment/gimi-worker -n $Namespace --timeout=180s

    Write-Host ""
    Write-Log "GIMI CI/CD deployed to Kubernetes!"
    Write-Host ""
    Write-Host "  Namespace: $Namespace"
    Write-Host "  Context:   $context"
    Write-Host ""
    Write-Host "  Port-forward: kubectl port-forward svc/gimi-server 8080:8080 -n $Namespace"
    Write-Host "  Logs:         kubectl logs -f deploy/gimi-server -n $Namespace"
    Write-Host "  Scale:        kubectl scale deploy/gimi-worker --replicas=10 -n $Namespace"
}

# ── Deploy: Helm ─────────────────────────────────────────────────────────────

function Deploy-Helm {
    Write-Header "Deploying GIMI CI/CD — Helm Chart"
    Assert-Helm

    $helmArgs = @(
        "upgrade", "--install", "gimi", "$RootDir\deploy\helm\gimi",
        "--namespace", $Namespace, "--create-namespace",
        "--set", "server.replicas=2",
        "--set", "worker.replicas=$Workers",
        "--set", "ingress.host=$Domain",
        "--set", "secrets.jwtSecret=$JwtSecret",
        "--set", "secrets.adminPassword=$AdminPassword",
        "--wait", "--timeout", "5m"
    )

    if ($ValuesFile) {
        $helmArgs += @("--values", $ValuesFile)
    }

    Write-Log "Installing/upgrading GIMI Helm chart..."
    & helm $helmArgs

    Write-Host ""
    Write-Log "GIMI CI/CD deployed via Helm!"
    Write-Host ""
    Write-Host "  Release:   gimi"
    Write-Host "  Namespace: $Namespace"
    Write-Host "  Domain:    $Domain"
    Write-Host ""
    Write-Host "  Status:    helm status gimi -n $Namespace"
    Write-Host "  Rollback:  helm rollback gimi -n $Namespace"
    Write-Host "  Uninstall: helm uninstall gimi -n $Namespace"
}

# ── Deploy: AWS ──────────────────────────────────────────────────────────────

function Deploy-AWS {
    Write-Header "Deploying GIMI CI/CD — AWS"
    Assert-Command "aws" "https://aws.amazon.com/cli/"
    Assert-Terraform

    $awsRegion = if ($Region) { $Region } elseif ($env:GIMI_AWS_REGION) { $env:GIMI_AWS_REGION } else { "us-east-1" }
    Write-Log "Region: $awsRegion"

    Set-Location "$RootDir\deploy\terraform\aws"

    Write-Log "Initializing Terraform..."
    terraform init

    Write-Log "Planning infrastructure..."
    terraform plan `
        -var="region=$awsRegion" `
        -var="domain=$Domain" `
        -var="admin_password=$AdminPassword" `
        -var="jwt_secret=$JwtSecret" `
        -var="worker_count=$Workers" `
        -out=tfplan

    Write-Log "Applying infrastructure..."
    terraform apply tfplan

    Write-Host ""
    Write-Log "GIMI CI/CD deployed to AWS!"
    terraform output
}

# ── Deploy: GCP ──────────────────────────────────────────────────────────────

function Deploy-GCP {
    Write-Header "Deploying GIMI CI/CD — Google Cloud"
    Assert-Command "gcloud" "https://cloud.google.com/sdk/docs/install"
    Assert-Terraform

    $gcpRegion = if ($Region) { $Region } elseif ($env:GIMI_GCP_REGION) { $env:GIMI_GCP_REGION } else { "us-central1" }
    $gcpProject = gcloud config get-value project 2>$null
    Write-Log "Project: $gcpProject, Region: $gcpRegion"

    Set-Location "$RootDir\deploy\terraform\gcp"

    terraform init
    terraform plan `
        -var="project=$gcpProject" `
        -var="region=$gcpRegion" `
        -var="domain=$Domain" `
        -var="admin_password=$AdminPassword" `
        -var="jwt_secret=$JwtSecret" `
        -var="worker_count=$Workers" `
        -out=tfplan

    terraform apply tfplan

    Write-Host ""
    Write-Log "GIMI CI/CD deployed to GCP!"
    terraform output
}

# ── Deploy: Azure ────────────────────────────────────────────────────────────

function Deploy-Azure {
    Write-Header "Deploying GIMI CI/CD — Azure"
    Assert-Command "az" "https://learn.microsoft.com/en-us/cli/azure/install-azure-cli-windows"
    Assert-Terraform

    $azRegion = if ($Region) { $Region } elseif ($env:GIMI_AZURE_REGION) { $env:GIMI_AZURE_REGION } else { "eastus" }
    Write-Log "Region: $azRegion"

    Set-Location "$RootDir\deploy\terraform\azure"

    terraform init
    terraform plan `
        -var="location=$azRegion" `
        -var="domain=$Domain" `
        -var="admin_password=$AdminPassword" `
        -var="jwt_secret=$JwtSecret" `
        -var="worker_count=$Workers" `
        -out=tfplan

    terraform apply tfplan

    Write-Host ""
    Write-Log "GIMI CI/CD deployed to Azure!"
    terraform output
}

# ── Help ─────────────────────────────────────────────────────────────────────

function Show-Help {
    Write-Host ""
    Write-Host "GIMI CI/CD — Windows PowerShell Deployment Script" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\deploy.ps1 <Mode> [-Workers 5] [-Domain ci.myco.com] [-Region us-west-2]"
    Write-Host ""
    Write-Host "Modes:" -ForegroundColor Yellow
    Write-Host "  local         Docker Compose on local machine"
    Write-Host "  docker        Docker Compose with production settings"
    Write-Host "  kubernetes    Deploy to any Kubernetes cluster"
    Write-Host "  helm          Deploy via Helm chart"
    Write-Host "  aws           AWS (ECS Fargate + RDS + ElastiCache)"
    Write-Host "  gcp           GCP (Cloud Run + Cloud SQL + Memorystore)"
    Write-Host "  azure         Azure (Container Apps + PostgreSQL + Redis)"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Yellow
    Write-Host "  .\deploy.ps1 local"
    Write-Host "  .\deploy.ps1 local -Workers 5"
    Write-Host "  .\deploy.ps1 kubernetes -Namespace my-ns"
    Write-Host "  .\deploy.ps1 helm -Domain ci.mycompany.com -ValuesFile .\my-values.yaml"
    Write-Host "  .\deploy.ps1 aws -Region us-west-2 -Workers 10"
    Write-Host ""
}

# ── Main ─────────────────────────────────────────────────────────────────────

switch ($Mode) {
    "local"       { Deploy-Local }
    "docker"      { Deploy-Docker }
    "kubernetes"  { Deploy-Kubernetes }
    "helm"        { Deploy-Helm }
    "aws"         { Deploy-AWS }
    "gcp"         { Deploy-GCP }
    "azure"       { Deploy-Azure }
    default       { Show-Help }
}
