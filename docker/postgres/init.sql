-- PostgreSQL 초기화 스크립트
-- demo-api가 사용하는 데이터베이스 설정

-- 확장 모듈
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 타임존 설정
SET timezone = 'Asia/Seoul';

-- products 테이블은 Spring JPA가 자동 생성하므로 여기서는 추가 설정만
-- 로그용 감사 테이블 (예시)
CREATE TABLE IF NOT EXISTS audit_events (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 초기 감사 이벤트
INSERT INTO audit_events (event_type, description)
VALUES ('SYSTEM_START', 'PostgreSQL 초기화 완료');
