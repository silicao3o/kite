-- ============================================
-- docker-monitor (lite-k8s) DDL
-- PostgreSQL
-- ============================================

-- healing_events
CREATE TABLE IF NOT EXISTS healing_events (
    id              VARCHAR(36)  PRIMARY KEY,
    container_id    VARCHAR(255),
    container_name  VARCHAR(255),
    timestamp       TIMESTAMP,
    success         BOOLEAN      NOT NULL DEFAULT FALSE,
    restart_count   INTEGER      NOT NULL DEFAULT 0,
    message         VARCHAR(255)
);

-- nodes
CREATE TABLE IF NOT EXISTS nodes (
    id                   VARCHAR(36)  PRIMARY KEY,
    name                 VARCHAR(255),
    host                 VARCHAR(255),
    port                 INTEGER      NOT NULL DEFAULT 0,
    connection_type      VARCHAR(10)  NOT NULL DEFAULT 'TCP',
    ssh_port             INTEGER      NOT NULL DEFAULT 0,
    ssh_user             VARCHAR(255),
    ssh_key_path         VARCHAR(255),
    status               VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN',
    cpu_usage_percent    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    memory_usage_percent DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    running_containers   INTEGER      NOT NULL DEFAULT 0,
    last_heartbeat       TIMESTAMP
);

-- incident_reports
CREATE TABLE IF NOT EXISTS incident_reports (
    id             VARCHAR(36)  PRIMARY KEY,
    container_id   VARCHAR(255),
    container_name VARCHAR(255),
    summary        TEXT,
    root_cause     TEXT,
    status         VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    created_at     TIMESTAMP    NOT NULL,
    closed_at      TIMESTAMP
);

-- incident_reports.timeline (@ElementCollection)
CREATE TABLE IF NOT EXISTS incident_timeline (
    incident_id VARCHAR(36)  NOT NULL REFERENCES incident_reports(id),
    entry       TEXT
);

-- incident_reports.suggestions (@ElementCollection)
CREATE TABLE IF NOT EXISTS incident_suggestions (
    incident_id VARCHAR(36)  NOT NULL REFERENCES incident_reports(id),
    suggestion  TEXT
);

-- suggestions
CREATE TABLE IF NOT EXISTS suggestions (
    id                       VARCHAR(36)  PRIMARY KEY,
    container_name           VARCHAR(255),
    type                     VARCHAR(30)
        CHECK (type IN ('CONFIG_OPTIMIZATION', 'PLAYBOOK_IMPROVEMENT', 'GENERAL')),
    content                  TEXT,
    status                   VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    created_at               TIMESTAMP    NOT NULL,
    pattern_occurrence_count INTEGER      NOT NULL DEFAULT 0
);

-- audit_logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id                VARCHAR(36)   PRIMARY KEY,
    timestamp         TIMESTAMP,
    container_name    VARCHAR(255),
    container_id      VARCHAR(255),
    playbook_name     VARCHAR(255),
    action_type       VARCHAR(255),
    intent            VARCHAR(255),
    reasoning         TEXT,
    risk_level        VARCHAR(10)
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    approval_required BOOLEAN       NOT NULL DEFAULT FALSE,
    approved_by       VARCHAR(255),
    approved          BOOLEAN       NOT NULL DEFAULT FALSE,
    execution_result  VARCHAR(10)
        CHECK (execution_result IN ('PENDING', 'SUCCESS', 'FAILURE', 'BLOCKED', 'TIMEOUT')),
    result_message    VARCHAR(255),
    completed_at      TIMESTAMP
);

-- pending_approvals
CREATE TABLE IF NOT EXISTS pending_approvals (
    id             VARCHAR(36)  PRIMARY KEY,
    playbook_name  VARCHAR(255),
    container_name VARCHAR(255),
    risk_level     VARCHAR(10)
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status         VARCHAR(10)
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    requested_at   TIMESTAMP,
    expires_at     TIMESTAMP,
    approved_by    VARCHAR(255),
    resolved_at    TIMESTAMP
);

-- ai_settings (단일 행: id = 'default')
CREATE TABLE IF NOT EXISTS ai_settings (
    id                VARCHAR(36)   PRIMARY KEY DEFAULT 'default',
    enabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    provider          VARCHAR(20)   NOT NULL DEFAULT 'ANTHROPIC',
    anthropic_api_key VARCHAR(255)  NOT NULL DEFAULT '',
    openai_api_key    VARCHAR(255)  NOT NULL DEFAULT '',
    gemini_api_key    VARCHAR(255)  NOT NULL DEFAULT '',
    anthropic_model   VARCHAR(100)  NOT NULL DEFAULT 'claude-haiku-4-5-20251001',
    openai_model      VARCHAR(100)  NOT NULL DEFAULT 'gpt-4o-mini',
    gemini_model      VARCHAR(100)  NOT NULL DEFAULT 'gemini-2.0-flash'
);

-- ai_settings 기본 행 삽입
INSERT INTO ai_settings (id) VALUES ('default') ON CONFLICT DO NOTHING;
