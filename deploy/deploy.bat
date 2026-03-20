@echo off
setlocal enabledelayedexpansion

:: ─────────────────────────────────────────────────────────────────────────────
:: GIMI CI/CD — Windows One-Click Deploy Script
:: Deploys to: local Docker, Kubernetes, Helm, AWS, GCP, Azure
:: ─────────────────────────────────────────────────────────────────────────────

set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."

:: Defaults
set "DEPLOY_MODE=%~1"
if not defined GIMI_NAMESPACE set "GIMI_NAMESPACE=gimi"
if not defined GIMI_DOMAIN set "GIMI_DOMAIN=gimi.example.com"
if not defined GIMI_WORKERS set "GIMI_WORKERS=3"
if not defined GIMI_ADMIN_PASSWORD set "GIMI_ADMIN_PASSWORD=changeme123"
if not defined GIMI_JWT_SECRET set "GIMI_JWT_SECRET=dev-jwt-secret-change-in-prod"

if "%DEPLOY_MODE%"=="" goto :usage
if "%DEPLOY_MODE%"=="-h" goto :usage
if "%DEPLOY_MODE%"=="--help" goto :usage
if "%DEPLOY_MODE%"=="local" goto :deploy_local
if "%DEPLOY_MODE%"=="docker" goto :deploy_docker
if "%DEPLOY_MODE%"=="kubernetes" goto :deploy_kubernetes
if "%DEPLOY_MODE%"=="helm" goto :deploy_helm
if "%DEPLOY_MODE%"=="aws" goto :deploy_aws
if "%DEPLOY_MODE%"=="gcp" goto :deploy_gcp
if "%DEPLOY_MODE%"=="azure" goto :deploy_azure

echo [ERROR] Unknown mode: %DEPLOY_MODE%
goto :usage

:: ─────────────────────────────────────────────────────────────────────────────
:: Usage
:: ─────────────────────────────────────────────────────────────────────────────
:usage
echo.
echo GIMI CI/CD — Windows Deployment Script
echo.
echo Usage: deploy.bat ^<mode^> [options]
echo.
echo Deployment Modes:
echo   local         Docker Compose on local machine (dev/test)
echo   docker        Docker Compose with production settings
echo   kubernetes    Deploy to any Kubernetes cluster (EKS/GKE/AKS/k3s)
echo   helm          Deploy via Helm chart to Kubernetes
echo   aws           Full AWS deployment (ECS Fargate + RDS + ElastiCache)
echo   gcp           Full GCP deployment (Cloud Run + Cloud SQL + Memorystore)
echo   azure         Full Azure deployment (Container Apps + PostgreSQL + Redis)
echo.
echo Quick Start:
echo   deploy.bat local                    Run locally with Docker Desktop
echo   deploy.bat kubernetes               Deploy to current kubectl context
echo   deploy.bat helm                     Helm-based deploy
echo.
echo Environment Variables:
echo   GIMI_NAMESPACE        Kubernetes namespace (default: gimi)
echo   GIMI_DOMAIN           Domain for ingress (default: gimi.example.com)
echo   GIMI_ADMIN_PASSWORD   Admin password
echo   GIMI_JWT_SECRET       JWT signing secret
echo   GIMI_WORKERS          Number of workers (default: 3)
echo   GIMI_DB_URL           External PostgreSQL URL (optional)
echo   GIMI_REDIS_URL        External Redis URL (optional)
echo.
echo Examples:
echo   set GIMI_WORKERS=5 ^&^& deploy.bat local
echo   set GIMI_DOMAIN=ci.mycompany.com ^&^& deploy.bat kubernetes
echo   deploy.bat aws
echo   deploy.bat helm
echo.
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Prerequisites
:: ─────────────────────────────────────────────────────────────────────────────
:check_docker
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is required but not installed.
    echo         Install Docker Desktop: https://www.docker.com/products/docker-desktop
    exit /b 1
)
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker Desktop is not running. Start it first.
    exit /b 1
)
exit /b 0

:check_kubectl
where kubectl >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] kubectl is required but not installed.
    echo         Install: https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/
    exit /b 1
)
kubectl cluster-info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] No Kubernetes cluster connected. Configure kubectl first.
    exit /b 1
)
exit /b 0

:check_helm
call :check_kubectl
if %errorlevel% neq 0 exit /b 1
where helm >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Helm is required but not installed.
    echo         Install: https://helm.sh/docs/intro/install/
    exit /b 1
)
exit /b 0

:check_terraform
where terraform >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Terraform is required but not installed.
    echo         Install: https://developer.hashicorp.com/terraform/install
    exit /b 1
)
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Deploy: Local (Docker Compose)
:: ─────────────────────────────────────────────────────────────────────────────
:deploy_local
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Deploying GIMI CI/CD — Local Docker Compose
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :check_docker
if %errorlevel% neq 0 exit /b 1

cd /d "%ROOT_DIR%"

echo [GIMI] Building images...
docker compose build
if %errorlevel% neq 0 (
    echo [ERROR] Docker build failed.
    exit /b 1
)

echo [GIMI] Starting services (server + %GIMI_WORKERS% workers + UI + PostgreSQL + Redis)...
docker compose up -d --scale gimi-worker=%GIMI_WORKERS%
if %errorlevel% neq 0 (
    echo [ERROR] Docker compose up failed.
    exit /b 1
)

echo [GIMI] Waiting for services to be healthy...
set "HEALTHY=0"
for /l %%i in (1,1,30) do (
    if !HEALTHY! equ 0 (
        curl -sf http://localhost:8080/actuator/health >nul 2>&1
        if !errorlevel! equ 0 (
            set "HEALTHY=1"
        ) else (
            timeout /t 2 /nobreak >nul
        )
    )
)

echo.
echo [GIMI] GIMI CI/CD is running!
echo.
echo   UI:     http://localhost:3000
echo   API:    http://localhost:8080
echo   Health: http://localhost:8080/actuator/health
echo.
echo   Admin password: %GIMI_ADMIN_PASSWORD%
echo.
echo   Stop:   docker compose down
echo   Logs:   docker compose logs -f
echo   Scale:  docker compose up -d --scale gimi-worker=10
echo.
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Deploy: Docker (Production)
:: ─────────────────────────────────────────────────────────────────────────────
:deploy_docker
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Deploying GIMI CI/CD — Production Docker Compose
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :check_docker
if %errorlevel% neq 0 exit /b 1

cd /d "%ROOT_DIR%"

echo [GIMI] Building optimized production images...
docker compose build

echo [GIMI] Starting with production settings...
set "GIMI_PROFILE=prod"
docker compose up -d --scale gimi-worker=%GIMI_WORKERS%

echo.
echo [GIMI] GIMI CI/CD deployed in production Docker mode.
echo   Workers: %GIMI_WORKERS%
echo   Admin password: %GIMI_ADMIN_PASSWORD%
echo.
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Deploy: Kubernetes
:: ─────────────────────────────────────────────────────────────────────────────
:deploy_kubernetes
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Deploying GIMI CI/CD — Kubernetes
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :check_kubectl
if %errorlevel% neq 0 exit /b 1

for /f "tokens=*" %%i in ('kubectl config current-context') do set "K8S_CONTEXT=%%i"
echo [GIMI] Deploying to cluster: %K8S_CONTEXT%
echo [GIMI] Namespace: %GIMI_NAMESPACE%

:: Create namespace
kubectl create namespace %GIMI_NAMESPACE% --dry-run=client -o yaml | kubectl apply -f -

:: Generate secrets
echo [GIMI] Configuring secrets...
kubectl create secret generic gimi-secrets ^
    --namespace %GIMI_NAMESPACE% ^
    --from-literal=postgres-url="jdbc:postgresql://postgres:5432/gimi" ^
    --from-literal=postgres-username=gimi ^
    --from-literal=postgres-password=%GIMI_ADMIN_PASSWORD% ^
    --from-literal=jwt-secret=%GIMI_JWT_SECRET% ^
    --from-literal=webhook-secret=webhook-secret-change-me ^
    --from-literal=redis-url="redis://redis:6379" ^
    --from-literal=admin-password=%GIMI_ADMIN_PASSWORD% ^
    --from-literal=worker-token=worker-token-change-me ^
    --dry-run=client -o yaml | kubectl apply -f -

:: Apply manifests
echo [GIMI] Applying Kubernetes manifests...
kubectl apply -k "%ROOT_DIR%\k8s\base\" --namespace %GIMI_NAMESPACE%

:: Wait for rollout
echo [GIMI] Waiting for deployments...
kubectl rollout status deployment/gimi-server -n %GIMI_NAMESPACE% --timeout=180s
kubectl rollout status deployment/gimi-worker -n %GIMI_NAMESPACE% --timeout=180s

echo.
echo [GIMI] GIMI CI/CD deployed to Kubernetes!
echo.
echo   Namespace: %GIMI_NAMESPACE%
echo   Context:   %K8S_CONTEXT%
echo.
echo   Port-forward: kubectl port-forward svc/gimi-server 8080:8080 -n %GIMI_NAMESPACE%
echo   Logs:         kubectl logs -f deploy/gimi-server -n %GIMI_NAMESPACE%
echo   Scale:        kubectl scale deploy/gimi-worker --replicas=10 -n %GIMI_NAMESPACE%
echo.
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Deploy: Helm
:: ─────────────────────────────────────────────────────────────────────────────
:deploy_helm
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Deploying GIMI CI/CD — Helm Chart
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

call :check_helm
if %errorlevel% neq 0 exit /b 1

echo [GIMI] Installing/upgrading GIMI Helm chart...
helm upgrade --install gimi "%ROOT_DIR%\deploy\helm\gimi" ^
    --namespace %GIMI_NAMESPACE% --create-namespace ^
    --set server.replicas=2 ^
    --set worker.replicas=%GIMI_WORKERS% ^
    --set ingress.host=%GIMI_DOMAIN% ^
    --set secrets.jwtSecret=%GIMI_JWT_SECRET% ^
    --set secrets.adminPassword=%GIMI_ADMIN_PASSWORD% ^
    --wait --timeout 5m

echo.
echo [GIMI] GIMI CI/CD deployed via Helm!
echo.
echo   Release:   gimi
echo   Namespace: %GIMI_NAMESPACE%
echo   Domain:    %GIMI_DOMAIN%
echo.
echo   Status:    helm status gimi -n %GIMI_NAMESPACE%
echo   Upgrade:   helm upgrade gimi %ROOT_DIR%\deploy\helm\gimi -n %GIMI_NAMESPACE%
echo   Rollback:  helm rollback gimi -n %GIMI_NAMESPACE%
echo   Uninstall: helm uninstall gimi -n %GIMI_NAMESPACE%
echo.
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Deploy: AWS
:: ─────────────────────────────────────────────────────────────────────────────
:deploy_aws
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Deploying GIMI CI/CD — AWS
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

where aws >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] AWS CLI is required. Install: https://aws.amazon.com/cli/
    exit /b 1
)
call :check_terraform
if %errorlevel% neq 0 exit /b 1

if not defined GIMI_AWS_REGION set "GIMI_AWS_REGION=us-east-1"
echo [GIMI] Region: %GIMI_AWS_REGION%

cd /d "%ROOT_DIR%\deploy\terraform\aws"

echo [GIMI] Initializing Terraform...
terraform init

echo [GIMI] Planning infrastructure...
terraform plan ^
    -var="region=%GIMI_AWS_REGION%" ^
    -var="domain=%GIMI_DOMAIN%" ^
    -var="admin_password=%GIMI_ADMIN_PASSWORD%" ^
    -var="jwt_secret=%GIMI_JWT_SECRET%" ^
    -var="worker_count=%GIMI_WORKERS%" ^
    -out=tfplan

echo [GIMI] Applying infrastructure...
terraform apply tfplan

echo.
echo [GIMI] GIMI CI/CD deployed to AWS!
terraform output
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Deploy: GCP
:: ─────────────────────────────────────────────────────────────────────────────
:deploy_gcp
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Deploying GIMI CI/CD — Google Cloud
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

where gcloud >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] gcloud CLI is required. Install: https://cloud.google.com/sdk/docs/install
    exit /b 1
)
call :check_terraform
if %errorlevel% neq 0 exit /b 1

if not defined GIMI_GCP_REGION set "GIMI_GCP_REGION=us-central1"
for /f "tokens=*" %%i in ('gcloud config get-value project 2^>nul') do set "GIMI_GCP_PROJECT=%%i"
echo [GIMI] Project: %GIMI_GCP_PROJECT%, Region: %GIMI_GCP_REGION%

cd /d "%ROOT_DIR%\deploy\terraform\gcp"

terraform init
terraform plan ^
    -var="project=%GIMI_GCP_PROJECT%" ^
    -var="region=%GIMI_GCP_REGION%" ^
    -var="domain=%GIMI_DOMAIN%" ^
    -var="admin_password=%GIMI_ADMIN_PASSWORD%" ^
    -var="jwt_secret=%GIMI_JWT_SECRET%" ^
    -var="worker_count=%GIMI_WORKERS%" ^
    -out=tfplan

terraform apply tfplan

echo.
echo [GIMI] GIMI CI/CD deployed to GCP!
terraform output
exit /b 0

:: ─────────────────────────────────────────────────────────────────────────────
:: Deploy: Azure
:: ─────────────────────────────────────────────────────────────────────────────
:deploy_azure
echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Deploying GIMI CI/CD — Azure
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo.

where az >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Azure CLI is required. Install: https://learn.microsoft.com/en-us/cli/azure/install-azure-cli-windows
    exit /b 1
)
call :check_terraform
if %errorlevel% neq 0 exit /b 1

if not defined GIMI_AZURE_REGION set "GIMI_AZURE_REGION=eastus"
echo [GIMI] Region: %GIMI_AZURE_REGION%

cd /d "%ROOT_DIR%\deploy\terraform\azure"

terraform init
terraform plan ^
    -var="location=%GIMI_AZURE_REGION%" ^
    -var="domain=%GIMI_DOMAIN%" ^
    -var="admin_password=%GIMI_ADMIN_PASSWORD%" ^
    -var="jwt_secret=%GIMI_JWT_SECRET%" ^
    -var="worker_count=%GIMI_WORKERS%" ^
    -out=tfplan

terraform apply tfplan

echo.
echo [GIMI] GIMI CI/CD deployed to Azure!
terraform output
exit /b 0
