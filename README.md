# GIMI CI/CD Platform

Enterprise-grade CI/CD pipeline engine with a Harness-style UI, distributed execution, DORA metrics, feature flags, OPA governance, and 23 REST API controllers.

## Architecture

```
                    +------------------+
                    |    gimi-ui       |   React 19 + TypeScript + Tailwind
                    |   (port 3000)    |   16 pages, DAG visualization
                    +--------+---------+
                             |
                    +--------v---------+
                    |   gimi-server    |   Spring Boot 3 + JWT auth
                    |   (port 8080)    |   23 REST controllers
                    +--+-----+-----+--+
                       |     |     |
              +--------+  +--+--+  +--------+
              |           |     |           |
        +-----v----+ +---v---+ +----v-----+
        | PostgreSQL| | Redis | | Workers  |  gimi-worker (N instances)
        |  (5432)   | | (6379)| | (8081+)  |  poll Redis queue
        +-----------+ +-------+ +----------+
```

## Quick Start

### Option 1: Docker Compose (recommended)

```bash
# Start everything (server + 1 worker + UI + PostgreSQL + Redis)
docker compose up -d

# Scale workers for high availability
docker compose up -d --scale gimi-worker=3

# Open the UI
open http://localhost:3000
```

Login: `admin` / `admin1234`

### Option 2: Local Development

**Prerequisites:** Java 21+, Maven 3.9+, Node 22+, PostgreSQL 16+

```bash
# 1. Start PostgreSQL
sudo -u postgres createdb gimi
sudo -u postgres psql -d gimi -c "CREATE USER gimi WITH PASSWORD 'gimi';"
sudo -u postgres psql -d gimi -c "GRANT ALL ON DATABASE gimi TO gimi; ALTER DATABASE gimi OWNER TO gimi;"
sudo -u postgres psql -d gimi -f gimi-engine/src/main/resources/db/migration/V1__init.sql
sudo -u postgres psql -d gimi -c "GRANT ALL ON ALL TABLES IN SCHEMA public TO gimi;"

# 2. Build
mvn compile

# 3. Start server (single-node mode)
mvn spring-boot:run -pl gimi-server

# 4. Start UI
cd gimi-ui && npm install && npm run dev

# 5. Open http://localhost:3000
```

### Option 3: HA / Distributed Mode

```bash
# 1. Start Redis
redis-server --daemonize yes

# 2. Start server with distributed mode
mvn spring-boot:run -pl gimi-server \
  -Dspring-boot.run.arguments="--gimi.server.distributed-mode=true"

# 3. Start workers (run on multiple machines for HA)
mvn spring-boot:run -pl gimi-worker
mvn spring-boot:run -pl gimi-worker -Dspring-boot.run.arguments="--server.port=8082"

# 4. Start UI
cd gimi-ui && npm run dev
```

## First Steps After Login

### 1. Create a Pipeline

Place a YAML file in the `pipelines/` directory:

```yaml
# pipelines/my-app.yaml
name: my-app
version: "1.0"
triggers:
  - type: git
    branch: main
    event: push
stages:
  - name: build
    steps:
      - name: compile
        type: docker
        image: maven:3.9
        commands:
          - mvn clean package
  - name: test
    depends_on: [build]
    steps:
      - name: unit-tests
        type: docker
        image: maven:3.9
        commands:
          - mvn test
  - name: deploy
    depends_on: [test]
    steps:
      - name: deploy-k8s
        type: docker
        image: bitnami/kubectl
        commands:
          - kubectl apply -f k8s/
```

### 2. Run the Pipeline

```bash
# Via API
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}' | jq -r .token)

curl -X POST http://localhost:8080/api/runs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pipelineName":"my-app","environment":"staging"}'
```

Or click **Run Pipeline** in the UI.

### 3. Set Up a Webhook

```bash
# GitHub webhook → triggers matching pipelines
curl -X POST http://localhost:8080/webhook \
  -H "Content-Type: application/json" \
  -d '{"ref":"refs/heads/main","repository":{"full_name":"org/repo","clone_url":"https://github.com/org/repo.git"}}'
```

## API Reference

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register user (first user becomes admin) |
| POST | `/api/auth/login` | Login, returns JWT token |
| GET | `/api/auth/me` | Current user info |
| POST | `/api/auth/api-keys` | Create API key |

### Pipelines
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/pipelines` | List all pipelines |
| GET | `/api/pipelines/{name}` | Pipeline detail |
| GET | `/api/pipelines/{name}/graph` | DAG visualization |
| POST | `/api/pipelines/validate` | Validate pipeline YAML |

### Executions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/runs` | Trigger pipeline run |
| GET | `/api/runs?limit=50` | List recent runs |
| GET | `/api/runs/{id}` | Run detail with stages |
| POST | `/api/runs/{id}/cancel` | Cancel running pipeline |
| GET | `/api/runs/{id}/jobs` | Jobs for a run |
| GET | `/api/logs/{runId}` | Execution logs |
| GET | `/api/logs/{runId}/stream` | SSE live log stream |

### Workers / Delegates
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/workers` | List workers |
| POST | `/api/workers/register` | Register worker |
| POST | `/api/workers/{id}/heartbeat` | Worker heartbeat |
| POST | `/api/workers/{id}/drain` | Drain worker |
| DELETE | `/api/workers/{id}` | Deregister worker |

### Feature Flags
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/feature-flags` | Create flag |
| GET | `/api/feature-flags` | List flags |
| GET | `/api/feature-flags/{key}` | Get flag |
| PUT | `/api/feature-flags/{key}` | Update flag |
| POST | `/api/feature-flags/{key}/evaluate` | Evaluate flag |

### Connectors
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/connectors` | Create connector |
| GET | `/api/connectors` | List connectors |
| GET | `/api/connectors/{id}` | Get connector |
| POST | `/api/connectors/{id}/test` | Test connection |

### Security & Governance
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/security/scan?runId={id}` | Run security scan |
| POST | `/api/policies` | Create OPA policy |
| GET | `/api/policies` | List policies |
| POST | `/api/policies/evaluate` | Evaluate policies |

### SLOs & Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/slo` | Create SLO |
| GET | `/api/slo` | List SLOs |
| GET | `/api/slo/{id}/status` | SLO status |
| GET | `/api/analytics/dora/{pipeline}?days=7` | DORA metrics |
| GET | `/api/analytics/pipeline/{name}` | Pipeline analytics |

### GitOps & Costs
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/gitops/configs/{name}` | Register GitOps config |
| GET | `/api/gitops/configs` | List configs |
| POST | `/api/costs` | Record cost |
| GET | `/api/costs/recommendations/{name}` | Cost optimization |

### Multi-Tenancy & RBAC
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/tenants/accounts` | Create account |
| GET | `/api/tenants/accounts` | List accounts |
| POST | `/api/rbac/role-bindings` | Assign role |
| GET | `/api/audit` | Audit trail |

## Configuration

### Server (`gimi-server`)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | Server port |
| `gimi.server.pipeline-dir` | `./pipelines` | Pipeline YAML directory |
| `gimi.server.distributed-mode` | `false` | Enable distributed execution |
| `gimi.server.jwt-secret` | (change me) | JWT signing secret |
| `gimi.server.postgres-url` | `jdbc:postgresql://localhost:5432/gimi` | Database URL |
| `gimi.server.redis-url` | `redis://localhost:6379` | Redis URL |
| `gimi.server.webhook-secret` | (empty) | Webhook validation secret |
| `gimi.server.s3-bucket` | `gimi-artifacts` | S3 artifact bucket |

### Worker (`gimi-worker`)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8081` | Worker port |
| `gimi.worker.max-concurrent-jobs` | `0` (auto) | Max jobs (0 = CPU cores * 4) |
| `gimi.worker.poll-interval-ms` | `500` | Queue poll interval |
| `gimi.worker.heartbeat-interval-ms` | `3000` | Heartbeat interval |
| `gimi.worker.redis-url` | `redis://localhost:6379` | Redis URL |
| `gimi.worker.server-url` | `http://localhost:8080` | Server URL for registration |
| `gimi.worker.labels` | `[default]` | Worker labels for job affinity |

### UI (`gimi-ui`)

| Property | Default | Description |
|----------|---------|-------------|
| Vite dev port | `3000` | Dev server port |
| API proxy | `/api` → `localhost:8080` | Backend proxy |
| Mock mode | `auto` | `mock` = always mock, `api` = always real, `auto` = mock when no token |

## UI Pages

| Page | Route | Features |
|------|-------|----------|
| Dashboard | `/` | Stats, deployment chart, DORA summary, recent runs |
| Pipelines | `/pipelines` | Pipeline list with status, tags, stage progress |
| Pipeline Studio | `/pipelines/:id` | DAG visualization (React Flow), triggers, execution history |
| Executions | `/executions` | Run table with status filters |
| Execution Detail | `/executions/:id` | Stage timeline, step breakdown, terminal log viewer |
| Delegates | `/workers` | Worker table with CPU/memory bars |
| DORA Metrics | `/analytics` | 4 charts: deploy freq, lead time, CFR, MTTR |
| SLOs | `/slos` | Gauge charts with error budget bars |
| Security Tests | `/security` | SAST/DAST/SCA results with severity |
| Feature Flags | `/feature-flags` | Per-environment toggles |
| Audit Trail | `/audit` | Action/resource/user tracking |
| Connectors | `/connectors` | External service integrations |
| Templates | `/templates` | Reusable pipeline templates |
| Notifications | `/notifications` | Channel configuration |
| Governance | `/governance` | OPA policy enforcement |
| Settings | `/settings` | Platform configuration |

## Pipeline YAML Schema

```yaml
name: string                    # Pipeline name (required)
version: string                 # Schema version (required)
variables:                      # Global variables
  KEY: value
triggers:                       # Auto-trigger configuration
  - type: git|cron|webhook
    branch: main
    event: push|pull_request|tag
stages:
  - name: string               # Stage name (required)
    depends_on: [stage-names]   # DAG dependencies
    steps:
      - name: string           # Step name (required)
        type: docker|shell      # Execution type (required)
        image: string           # Docker image (for type: docker)
        commands:               # Commands to run
          - string
        environment:            # Step-level env vars
          KEY: value
        timeout: 300            # Timeout in seconds
```

## Running Tests

```bash
# Backend unit tests
mvn test

# Frontend unit + component tests
cd gimi-ui && npm test

# Integration tests (requires running server)
bash gimi-ui/scripts/integration-test.sh

# All frontend tests (includes E2E when server is running)
cd gimi-ui && npm test
```

## Monitoring

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Health status (UP/DOWN) |
| `GET /actuator/prometheus` | Prometheus metrics |
| `GET /actuator/info` | Application info |
| `GET /actuator/metrics` | Spring Boot metrics |

## Project Modules

| Module | Description |
|--------|-------------|
| `gimi-core` | Domain models, pipeline schema (Pipeline, Stage, Step records) |
| `gimi-engine` | Parser, executor, DAG scheduler, execution store, services |
| `gimi-server` | Spring Boot REST API, security, controllers |
| `gimi-worker` | Distributed job poller, executor, health reporter |
| `gimi-cli` | Command-line interface |
| `gimi-ui` | React frontend (Vite + Tailwind + React Flow + Recharts) |

## License

See [LICENSE](LICENSE).
