# GIMI CI/CD Platform - Operations Runbook

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Local Development](#local-development)
4. [Building & Packaging](#building--packaging)
5. [Docker Images](#docker-images)
6. [Kubernetes Deployment](#kubernetes-deployment)
7. [Configuration Reference](#configuration-reference)
8. [Health Checks & Monitoring](#health-checks--monitoring)
9. [Scaling](#scaling)
10. [Common Operations](#common-operations)
11. [Troubleshooting](#troubleshooting)
12. [Security Checklist](#security-checklist)

---

## Architecture Overview

```
                    ┌──────────────┐
                    │   Ingress    │  (nginx, TLS via cert-manager)
                    │  :443/:80   │
                    └──────┬───────┘
                           │
              ┌────────────▼────────────┐
              │     gimi-server (x2)    │  REST API, auth, pipeline mgmt
              │     :8080               │  Spring Boot + Virtual Threads
              └────┬──────────┬─────────┘
                   │          │
            ┌──────▼──┐  ┌────▼─────┐
            │ Postgres │  │  Redis   │  Job queue + caching
            │  :5432   │  │  :6379   │
            └──────────┘  └────┬─────┘
                               │
              ┌────────────────▼────────────┐
              │   gimi-worker (x3, HPA→50)  │  Job execution
              │   :8081                     │
              └─────────────────────────────┘
```

**Modules:**
| Module | Purpose | Output |
|--------|---------|--------|
| `gimi-core` | Models, auth, execution records | Library JAR |
| `gimi-engine` | Parser, DAG planner, executors, queue, retry | Library JAR |
| `gimi-server` | REST API, controllers, JWT auth | Fat JAR (Spring Boot) |
| `gimi-worker` | Job polling, execution on worker nodes | Fat JAR (Spring Boot) |
| `gimi-cli` | CLI tool for pipeline management | Fat JAR |
| `gimi-ui` | React dashboard | Static files (nginx) |

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21+ | Build & runtime |
| Maven | 3.9+ | Build system |
| Docker | 24+ | Container images |
| kubectl | 1.28+ | K8s deployment |
| Node.js | 22+ | UI build (optional) |
| PostgreSQL | 15+ | Persistent storage |
| Redis | 7+ | Job queue |

---

## Local Development

### Start dependencies

```bash
# PostgreSQL
docker run -d --name gimi-pg \
  -e POSTGRES_DB=gimi -e POSTGRES_USER=gimi -e POSTGRES_PASSWORD=gimi \
  -p 5432:5432 postgres:15-alpine

# Redis
docker run -d --name gimi-redis -p 6379:6379 redis:7-alpine
```

### Build & run

```bash
# Full build with tests
mvn clean package

# Run server (port 8080)
java -jar gimi-server/target/gimi-server-1.0-SNAPSHOT.jar

# Run worker (port 8081) - in another terminal
java -jar gimi-worker/target/gimi-worker-1.0-SNAPSHOT.jar
```

### Environment overrides

```bash
# Server
export GIMI_SERVER_JWT_SECRET="your-secret-min-32-chars-long!!"
export GIMI_SERVER_REDIS_URL="redis://localhost:6379"
export GIMI_SERVER_POSTGRES_URL="jdbc:postgresql://localhost:5432/gimi"
export GIMI_SERVER_DEFAULT_ADMIN_PASSWORD="admin"

# Worker
export GIMI_WORKER_REDIS_URL="redis://localhost:6379"
export GIMI_WORKER_SERVER_URL="http://localhost:8080"
export GIMI_WORKER_MAX_CONCURRENT_JOBS=200
```

---

## Building & Packaging

```bash
# Build all modules (with tests)
mvn clean package

# Build specific module
mvn clean package -pl gimi-server -am

# Skip tests (CI artifact build)
mvn clean package -DskipTests

# Run only tests
mvn test
```

**Test suites:**
```bash
# Java unit tests (48 tests across 21 files)
mvn test

# Python integration tests (requires running server on localhost:8080)
python3 tests/concurrent_test.py      # 6 concurrency scenarios
python3 tests/stress_test_200.py       # 8 stress scenarios (200 threads)
python3 tests/chaos_30_scenarios.py    # 30 chaos/edge-case scenarios
```

---

## Docker Images

### Build

```bash
# Server image
docker build --target server -t gimi/server:latest .

# Worker image
docker build --target worker -t gimi/worker:latest .

# UI image
docker build -t gimi/ui:latest gimi-ui/
```

### Run locally with Docker

```bash
docker network create gimi-net

# PostgreSQL + Redis (on gimi-net)
docker run -d --name postgres --network gimi-net \
  -e POSTGRES_DB=gimi -e POSTGRES_USER=gimi -e POSTGRES_PASSWORD=gimi \
  postgres:15-alpine

docker run -d --name redis --network gimi-net redis:7-alpine

# Server
docker run -d --name gimi-server --network gimi-net -p 8080:8080 \
  -e GIMI_SERVER_POSTGRES_URL=jdbc:postgresql://postgres:5432/gimi \
  -e GIMI_SERVER_POSTGRES_USERNAME=gimi \
  -e GIMI_SERVER_POSTGRES_PASSWORD=gimi \
  -e GIMI_SERVER_REDIS_URL=redis://redis:6379 \
  -e GIMI_SERVER_JWT_SECRET="change-me-in-production-min-32-chars!!" \
  -e GIMI_SERVER_DISTRIBUTED_MODE=true \
  gimi/server:latest

# Worker
docker run -d --name gimi-worker --network gimi-net \
  -e GIMI_WORKER_REDIS_URL=redis://redis:6379 \
  -e GIMI_WORKER_SERVER_URL=http://gimi-server:8080 \
  -e GIMI_WORKER_MAX_CONCURRENT_JOBS=200 \
  gimi/worker:latest
```

---

## Kubernetes Deployment

### Namespace & security

The `gimi` namespace enforces **Pod Security Standards (restricted)**:
- Pods run as non-root (UID 1000)
- Read-only root filesystem
- All capabilities dropped
- Seccomp profile: RuntimeDefault

### Deploy

```bash
# Apply all manifests via Kustomize
kubectl apply -k k8s/base/

# Verify
kubectl -n gimi get pods,svc,ingress,hpa,networkpolicy
```

### Secrets (MUST change before production)

Edit `k8s/base/config.yaml` or use External Secrets Operator:

| Secret Key | Description | Notes |
|------------|-------------|-------|
| `postgres-url` | JDBC connection string | e.g., `jdbc:postgresql://rds-host:5432/gimi` |
| `postgres-username` | DB username | Use a dedicated service account |
| `postgres-password` | DB password | Generate strong random value |
| `jwt-secret` | JWT signing key | Min 32 characters, random |
| `webhook-secret` | Webhook HMAC secret | For GitHub/GitLab integration |
| `redis-url` | Redis connection | e.g., `redis://elasticache-host:6379` |
| `admin-password` | Initial admin password | Change after first login |
| `worker-token` | Worker auth token | Derive from JWT secret |

**Production recommendation:** Replace the `Secret` with `ExternalSecret` (see template in `config.yaml` comments) pointing to Vault, AWS Secrets Manager, or GCP Secret Manager.

### Network policies

Three policies enforce least-privilege:

| Policy | Allows |
|--------|--------|
| `default-deny-all` | Blocks all traffic by default |
| `gimi-server` | Ingress: nginx + workers. Egress: Postgres, Redis, DNS |
| `gimi-worker` | Ingress: health checks only. Egress: Redis, server API, DNS |

### TLS / Ingress

- Ingress controller: **nginx**
- TLS: **cert-manager** with `letsencrypt-prod` ClusterIssuer
- Rate limiting: 50 RPS, 20 concurrent connections per IP
- Security headers: X-Frame-Options DENY, X-Content-Type-Options nosniff, XSS-Protection
- Proxy timeouts: connect 10s, read/send 60s
- Max body: 100MB

Update the hostname in `k8s/base/ingress.yaml`:
```yaml
# Change gimi.example.com to your actual domain
tls:
  - hosts:
      - your-domain.com
    secretName: gimi-tls
rules:
  - host: your-domain.com
```

---

## Configuration Reference

### Server (`application.yml`)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | HTTP port |
| `server.tomcat.threads.max` | 400 | Max request threads |
| `server.tomcat.max-connections` | 20000 | Max connections |
| `gimi.server.distributed-mode` | false | Enable Redis-backed queue |
| `gimi.server.pipeline-dir` | ./pipelines | YAML pipeline directory |
| `gimi.server.jwt-secret` | (change-me) | JWT signing secret |
| `gimi.server.cors-allowed-origins` | "" | Allowed CORS origins (comma-separated) |
| `spring.threads.virtual.enabled` | true | JDK 21 virtual threads |

### Worker (`application.yml`)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8081 | HTTP port |
| `gimi.worker.max-concurrent-jobs` | 0 (auto) | 0 = CPU cores × 4 |
| `gimi.worker.poll-interval-ms` | 500 | Redis queue poll interval |
| `gimi.worker.heartbeat-interval-ms` | 3000 | Heartbeat to server |
| `gimi.worker.labels` | [default] | Worker capability labels |

### JVM tuning (K8s)

Set via `JAVA_TOOL_OPTIONS` env var in deployments:
```
-XX:MaxRAMPercentage=75.0 -XX:+UseZGC
```

---

## Health Checks & Monitoring

### Endpoints

| Endpoint | Purpose | Used By |
|----------|---------|---------|
| `/actuator/health` | Overall health | startupProbe |
| `/actuator/health/liveness` | Liveness (server) | livenessProbe |
| `/actuator/health/readiness` | Readiness (server, checks Redis + Postgres) | readinessProbe |
| `/actuator/prometheus` | Prometheus metrics | Monitoring stack |
| `/actuator/metrics` | Spring metrics | Debugging |
| `/actuator/info` | App info | Informational |

### Probe timing

| Probe | Initial Delay | Period | Failures | Timeout |
|-------|--------------|--------|----------|---------|
| Startup | 10s | 5s | 30 (= 2.5 min max) | 3s |
| Liveness | - | 10s | 3 | 3s |
| Readiness | - | 5s | 5 | 3s |

### Key metrics to monitor

- `http_server_requests_seconds` — API latency (P50, P95, P99)
- `jvm_memory_used_bytes` — JVM heap usage
- `system_cpu_usage` — CPU utilization
- `gimi_jobs_active` — Active concurrent jobs per worker
- `gimi_queue_depth` — Redis queue depth
- `hikaricp_connections_active` — DB connection pool

---

## Scaling

### Worker auto-scaling (HPA)

```yaml
minReplicas: 2
maxReplicas: 50
Scale-up:   5 pods per 60s  (stabilization: 30s)
Scale-down: 2 pods per 60s  (stabilization: 300s)
Triggers:   CPU > 70% avg, Memory > 80% avg
```

**Capacity planning:**
- Each worker: `max-concurrent-jobs=200` (default)
- 50 workers × 200 jobs = **10,000 concurrent pipeline jobs**
- Worker resources: 500m-4 CPU, 512Mi-4Gi memory

### Server scaling

- Deployed with 2 replicas (static)
- PDB: minAvailable=1 (always at least 1 running during rollouts)
- To scale: `kubectl -n gimi scale deployment gimi-server --replicas=N`

### Topology

Both server and worker use `topologySpreadConstraints` to spread pods across nodes (`maxSkew: 1`, `DoNotSchedule`).

---

## Common Operations

### Rolling deployment

```bash
# Update image and trigger rolling update
kubectl -n gimi set image deployment/gimi-server server=gimi/server:v1.2.3
kubectl -n gimi set image deployment/gimi-worker worker=gimi/worker:v1.2.3

# Watch rollout
kubectl -n gimi rollout status deployment/gimi-server
kubectl -n gimi rollout status deployment/gimi-worker
```

Rolling update strategy:
- **Server:** maxSurge=1, maxUnavailable=0 (zero-downtime)
- **Worker:** maxSurge=2, maxUnavailable=1 (faster rollout)

### Rollback

```bash
kubectl -n gimi rollout undo deployment/gimi-server
kubectl -n gimi rollout undo deployment/gimi-worker
```

### View logs

```bash
# Server logs
kubectl -n gimi logs -l app.kubernetes.io/name=gimi-server --tail=100 -f

# Worker logs
kubectl -n gimi logs -l app.kubernetes.io/name=gimi-worker --tail=100 -f

# Specific pod
kubectl -n gimi logs gimi-server-xxxxx -f
```

### Restart pods (no config change)

```bash
kubectl -n gimi rollout restart deployment/gimi-server
kubectl -n gimi rollout restart deployment/gimi-worker
```

### Check HPA status

```bash
kubectl -n gimi get hpa gimi-worker
kubectl -n gimi describe hpa gimi-worker
```

### API smoke test

```bash
# Health check
curl -s https://gimi.example.com/actuator/health | jq .

# Login
TOKEN=$(curl -s -X POST https://gimi.example.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"YOUR_PASSWORD"}' | jq -r .token)

# List pipelines
curl -s -H "Authorization: Bearer $TOKEN" \
  https://gimi.example.com/api/pipelines | jq .

# Trigger a pipeline
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  https://gimi.example.com/api/runs/trigger/my-pipeline | jq .
```

---

## Troubleshooting

### Pod won't start

```bash
kubectl -n gimi describe pod <pod-name>
kubectl -n gimi logs <pod-name> --previous
```

**Common causes:**
- Secret not found → Ensure `gimi-secrets` exists: `kubectl -n gimi get secret gimi-secrets`
- Image pull failure → Check image name/tag and registry credentials
- OOM killed → Increase memory limits or tune `MaxRAMPercentage`

### Readiness probe failing

Server readiness checks Redis + Postgres connectivity. Verify:

```bash
# Check from inside the pod
kubectl -n gimi exec -it <server-pod> -- sh
wget -qO- http://localhost:8080/actuator/health/readiness
```

**Common causes:**
- Postgres unreachable → Check NetworkPolicy allows egress to port 5432
- Redis unreachable → Check NetworkPolicy allows egress to port 6379
- DNS failure → Ensure DNS egress (port 53) is allowed in NetworkPolicy

### Worker not processing jobs

```bash
# Check worker logs for connection errors
kubectl -n gimi logs -l app.kubernetes.io/name=gimi-worker --tail=50

# Verify worker can reach Redis
kubectl -n gimi exec -it <worker-pod> -- sh -c 'wget -qO- http://localhost:8081/actuator/health'

# Check queue depth (if using Redis CLI)
kubectl exec -it redis-pod -- redis-cli LLEN gimi:job:queue
```

### High latency / slow responses

1. Check pod CPU/memory: `kubectl -n gimi top pods`
2. Check HPA: `kubectl -n gimi get hpa` — is it maxed out?
3. Check Tomcat thread pool: `curl .../actuator/metrics/tomcat.threads.busy`
4. Check DB connection pool: `curl .../actuator/metrics/hikaricp.connections.active`
5. Check ingress rate limiting — may be throttling at 50 RPS

### Network connectivity issues

The default-deny NetworkPolicy blocks everything not explicitly allowed. Verify:

```bash
kubectl -n gimi get networkpolicy
kubectl -n gimi describe networkpolicy gimi-server
```

If adding a new dependency (e.g., external API), update `network-policy.yaml` with the appropriate egress rule.

---

## Security Checklist

### Before going to production

- [ ] **Change all default secrets** in `k8s/base/config.yaml` (JWT, DB password, admin password, webhook secret, worker token)
- [ ] **Use External Secrets Operator** or Sealed Secrets instead of plain K8s Secrets
- [ ] **Set CORS origins** to your actual domain (`gimi-config` ConfigMap)
- [ ] **Update ingress hostname** from `gimi.example.com` to your domain
- [ ] **Verify cert-manager** is installed and `letsencrypt-prod` ClusterIssuer exists
- [ ] **Review NetworkPolicies** — ensure they match your actual database/Redis endpoints
- [ ] **Set up Prometheus** scraping `/actuator/prometheus` endpoints
- [ ] **Configure alerting** on: pod restarts, OOM kills, readiness failures, queue depth > threshold
- [ ] **Enable audit logging** for API access
- [ ] **Run integration tests** against the deployed environment
- [ ] **Test rollback** procedure at least once
- [ ] **Document on-call escalation** and add runbook link to alerting rules
