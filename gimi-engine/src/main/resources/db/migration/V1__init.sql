-- Gimi CI/CD schema initialization

CREATE TABLE executions (
    run_id        TEXT        PRIMARY KEY,
    pipeline_name TEXT        NOT NULL,
    status        TEXT        NOT NULL,
    trigger       TEXT,
    environment   TEXT,
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    data          JSONB       NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE jobs (
    id              TEXT        PRIMARY KEY,
    pipeline_name   TEXT        NOT NULL,
    run_id          TEXT        NOT NULL,
    stage_name      TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'QUEUED',
    worker_id       TEXT,
    pipeline_yaml   TEXT,
    variables       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    secrets         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    priority        INT         NOT NULL DEFAULT 0,
    max_retries     INT         NOT NULL DEFAULT 0,
    retry_count     INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    timeout_seconds BIGINT      NOT NULL DEFAULT 3600
);

CREATE TABLE workers (
    id                  TEXT        PRIMARY KEY,
    hostname            TEXT        NOT NULL,
    port                INT         NOT NULL,
    status              TEXT        NOT NULL DEFAULT 'OFFLINE',
    max_concurrent_jobs INT         NOT NULL DEFAULT 4,
    active_jobs         INT         NOT NULL DEFAULT 0,
    labels              JSONB       NOT NULL DEFAULT '[]'::jsonb,
    capabilities        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    last_heartbeat      TIMESTAMPTZ,
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             TEXT
);

CREATE TABLE artifacts (
    id           TEXT        PRIMARY KEY,
    run_id       TEXT        NOT NULL,
    stage_name   TEXT,
    step_name    TEXT,
    path         TEXT        NOT NULL,
    content_type TEXT,
    size_bytes   BIGINT      NOT NULL DEFAULT 0,
    checksum     TEXT,
    storage_key  TEXT        NOT NULL,
    metadata     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ
);

CREATE TABLE approvals (
    id            TEXT        PRIMARY KEY,
    run_id        TEXT        NOT NULL,
    pipeline_name TEXT,
    stage_name    TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'PENDING',
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    resolved_by   TEXT,
    comment       TEXT
);

CREATE TABLE users (
    id            TEXT        PRIMARY KEY,
    username      TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    email         TEXT,
    roles         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login    TIMESTAMPTZ
);

CREATE TABLE api_keys (
    id         TEXT        PRIMARY KEY,
    name       TEXT        NOT NULL,
    key_hash   TEXT        NOT NULL,
    owner_id   TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scopes     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    last_used  TIMESTAMPTZ
);

-- Indexes for common query patterns
CREATE INDEX idx_jobs_status  ON jobs(status);
CREATE INDEX idx_jobs_run_id  ON jobs(run_id);
CREATE INDEX idx_workers_status ON workers(status);
CREATE INDEX idx_artifacts_run_id ON artifacts(run_id);
CREATE INDEX idx_approvals_run_id_status ON approvals(run_id, status);
