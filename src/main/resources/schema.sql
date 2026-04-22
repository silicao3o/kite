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
    ssh_password         VARCHAR(255),
    ssh_key_path         VARCHAR(255),
    ssh_passphrase       VARCHAR(255),
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

-- self_healing_rules
CREATE TABLE IF NOT EXISTS self_healing_rules (
    id                    VARCHAR(36)  PRIMARY KEY,
    name_pattern          VARCHAR(255),
    max_restarts          INTEGER      NOT NULL DEFAULT 3,
    restart_delay_seconds INTEGER      NOT NULL DEFAULT 0,
    node_name             VARCHAR(255),
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL
);

-- Phase 7.15 notification_rules 테이블 제거 (Phase 7.17에서 email_subscriptions로 교체)
DROP TABLE IF EXISTS notification_rules;

-- email_subscriptions (Phase 7.17) — 수신자별 컨테이너/노드 구독 라우팅
CREATE TABLE IF NOT EXISTS email_subscriptions (
    id                  VARCHAR(36)  PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    container_pattern   VARCHAR(255),
    node_name           VARCHAR(255),
    notify_intentional  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL,
    CONSTRAINT chk_subscription_target
        CHECK (container_pattern IS NOT NULL OR node_name IS NOT NULL)
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

-- image_watches (이미지 감시 설정)
CREATE TABLE IF NOT EXISTS image_watches (
    id                     VARCHAR(36)  PRIMARY KEY,
    image                  VARCHAR(255) NOT NULL,
    tag                    VARCHAR(50)  NOT NULL DEFAULT 'latest',
    container_pattern      VARCHAR(255),
    node_names             TEXT         NOT NULL DEFAULT '[]',
    poll_interval_seconds  INTEGER      NOT NULL DEFAULT 300,
    max_unavailable        INTEGER      NOT NULL DEFAULT 1,
    mode                   VARCHAR(10)  NOT NULL DEFAULT 'POLLING',
    ghcr_token             VARCHAR(255),
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP    NOT NULL
);

-- 기존 image_watches 테이블 마이그레이션: node_name → node_names, poll_interval_seconds 추가
-- node_names 컬럼 추가 (기존 node_name 값 마이그레이션)
ALTER TABLE image_watches ADD COLUMN IF NOT EXISTS node_names TEXT DEFAULT '[]';
ALTER TABLE image_watches ADD COLUMN IF NOT EXISTS poll_interval_seconds INTEGER DEFAULT 300;
-- 기존 node_name 값이 있으면 node_names로 마이그레이션
UPDATE image_watches SET node_names = '["' || node_name || '"]' WHERE node_name IS NOT NULL AND node_names = '[]';
UPDATE image_watches SET poll_interval_seconds = 300 WHERE poll_interval_seconds IS NULL;
-- node_name 컬럼 제거 (PostgreSQL)
ALTER TABLE image_watches DROP COLUMN IF EXISTS node_name;
ALTER TABLE image_watches ADD COLUMN IF NOT EXISTS mode VARCHAR(10) DEFAULT 'POLLING';
UPDATE image_watches SET mode = 'POLLING' WHERE mode IS NULL;
ALTER TABLE image_watches ADD COLUMN IF NOT EXISTS image_registry_id VARCHAR(36);

-- 기존 와치의 image가 레지스트리에 없으면 자동 등록
INSERT INTO image_registry (id, image, created_at)
SELECT gen_random_uuid()::varchar, w.image, NOW()
FROM image_watches w
WHERE NOT EXISTS (SELECT 1 FROM image_registry r WHERE r.image = w.image)
GROUP BY w.image;

-- 기존 와치에 ghcr_token이 있으면 레지스트리로 이관
UPDATE image_registry r
SET ghcr_token = w.ghcr_token
FROM image_watches w
WHERE r.image = w.image
  AND w.ghcr_token IS NOT NULL
  AND w.ghcr_token != ''
  AND (r.ghcr_token IS NULL OR r.ghcr_token = '');

-- image_registry_id FK 채우기
UPDATE image_watches w
SET image_registry_id = r.id
FROM image_registry r
WHERE w.image = r.image
  AND w.image_registry_id IS NULL;

-- ghcr_token 컬럼 제거 (레지스트리 FK로 대체)
ALTER TABLE image_watches DROP COLUMN IF EXISTS ghcr_token;

-- image_update_history (이미지 업데이트 이력)
CREATE TABLE IF NOT EXISTS image_update_history (
    id              VARCHAR(36)  PRIMARY KEY,
    watch_id        VARCHAR(36)  NOT NULL,
    image           VARCHAR(255) NOT NULL,
    tag             VARCHAR(50),
    previous_digest VARCHAR(255),
    new_digest      VARCHAR(255),
    status          VARCHAR(10)  NOT NULL
        CHECK (status IN ('DETECTED', 'SUCCESS', 'FAILED')),
    node_name       VARCHAR(255),
    container_name  VARCHAR(255),
    message         TEXT,
    created_at      TIMESTAMP    NOT NULL
);

-- env_profiles (환경변수 프로파일)
CREATE TABLE IF NOT EXISTS env_profiles (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    type        VARCHAR(20)  NOT NULL DEFAULT 'DATABASE',
    description TEXT,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL
);

-- env_profile_entries (프로파일 엔트리)
CREATE TABLE IF NOT EXISTS env_profile_entries (
    id          VARCHAR(36)  PRIMARY KEY,
    profile_id  VARCHAR(36)  NOT NULL,
    entry_key   VARCHAR(255) NOT NULL,
    entry_value TEXT,
    secret      BOOLEAN      NOT NULL DEFAULT FALSE
);

-- service_definitions (서비스 정의 — Compose 관리)
CREATE TABLE IF NOT EXISTS service_definitions (
    id              VARCHAR(36)  PRIMARY KEY,
    name            VARCHAR(255) NOT NULL UNIQUE,
    compose_yaml    TEXT         NOT NULL,
    env_profile_id  VARCHAR(36),
    node_names      TEXT         NOT NULL DEFAULT '[]',
    status          VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP    NOT NULL
);

-- image_registry (이미지 경로 레지스트리)
CREATE TABLE IF NOT EXISTS image_registry (
    id          VARCHAR(36)  PRIMARY KEY,
    image       VARCHAR(255) NOT NULL UNIQUE,
    alias       VARCHAR(255),
    description TEXT,
    ghcr_token  VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL
);
ALTER TABLE image_registry ADD COLUMN IF NOT EXISTS ghcr_token VARCHAR(255);

-- env_profile_audit_logs (환경변수 프로파일 감사 로그)
CREATE TABLE IF NOT EXISTS env_profile_audit_logs (
    id                        VARCHAR(36)  PRIMARY KEY,
    timestamp                 TIMESTAMP    NOT NULL,
    profile_id                VARCHAR(36),
    profile_name              VARCHAR(255),
    action                    VARCHAR(20)  NOT NULL,
    actor                     VARCHAR(255),
    changed_keys              TEXT,
    before_hash               VARCHAR(255),
    after_hash                VARCHAR(255),
    referenced_container_name VARCHAR(255)
);
