# Docker Monitor 사용 가이드

> Docker 컨테이너 모니터링 및 AI 자가치유 시스템 상세 사용법

---

## 목차

1. [빠른 시작](#빠른-시작)
2. [설정](#설정)
3. [대시보드](#대시보드)
4. [자가치유 (Self-Healing)](#자가치유-self-healing)
5. [Playbook 시스템](#playbook-시스템)
6. [Safety Gate](#safety-gate)
7. [Human-in-the-Loop](#human-in-the-loop)
8. [Audit Logger](#audit-logger)
9. [AI 설정 (멀티 프로바이더)](#ai-설정-멀티-프로바이더)
10. [로그 수집 및 분석](#로그-수집-및-분석)
11. [장애 리포트](#장애-리포트)
12. [패턴 감지 및 제안](#패턴-감지-및-제안)
13. [메트릭 히스토리 & 집계](#메트릭-히스토리--집계)
14. [GHCR 이미지 자동 업데이트 (5.1)](#ghcr-이미지-자동-업데이트)
15. [Health Check Probe (5.2)](#health-check-probe)
16. [Desired State 관리 (5.3)](#desired-state-관리)
17. [다중 노드 스케줄링 (5.4)](#다중-노드-스케줄링)
18. [배포 전략 (5.5)](#배포-전략)
19. [멀티 노드 통합 모니터링 (Phase 6)](#멀티-노드-통합-모니터링-phase-6)
20. [알림 설정](#알림-설정)
21. [API 레퍼런스](#api-레퍼런스)
22. [WebSocket 사용법](#websocket-사용법)
23. [트러블슈팅](#트러블슈팅)

---

## 빠른 시작

### 1. 빌드

```bash
# 테스트 실행
mvn test

# 패키지 빌드
mvn clean package -DskipTests
```

### 2. 실행

```bash
# 기본 실행
java -jar target/docker-monitor-1.0.0.jar

# 환경변수로 설정
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
export ALERT_EMAIL=admin@example.com
export ANTHROPIC_API_KEY=sk-ant-...
java -jar target/docker-monitor-1.0.0.jar
```

### 3. Docker로 실행

```bash
docker run -d \
  --name docker-monitor \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 8080:8080 \
  -e MAIL_USERNAME=your-email@gmail.com \
  -e MAIL_PASSWORD=your-app-password \
  -e ALERT_EMAIL=admin@example.com \
  -e ANTHROPIC_API_KEY=sk-ant-... \
  docker-monitor
```

### 4. 대시보드 접속

브라우저에서 `http://localhost:8080` 접속

---

## 설정

### application.yml 기본 구조

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

docker:
  host: ${DOCKER_HOST:unix:///var/run/docker.sock}
  monitor:
    server-name: ${SERVER_NAME:Production-Server-01}
    log-tail-lines: ${LOG_TAIL_LINES:50}

    # 알림 설정
    notification:
      email:
        to: ${ALERT_EMAIL}
        from: ${MAIL_FROM:docker-monitor@example.com}

    # 메트릭 수집
    metrics:
      enabled: true
      collection-interval-seconds: 15
      cpu-threshold-percent: 80
      memory-threshold-percent: 90
      retention:
        retention-days: 30

    # 자가치유
    self-healing:
      enabled: true
      reset-window-minutes: 30
      rules:
        - name-pattern: "web-*"
          max-restarts: 3
          restart-delay-seconds: 10

    # AI 설정 (멀티 프로바이더)
    ai:
      enabled: true
      provider: ANTHROPIC          # ANTHROPIC | OPENAI | GEMINI
      anthropic-api-key: ${ANTHROPIC_API_KEY:}
      openai-api-key: ${OPENAI_API_KEY:}
      gemini-api-key: ${GEMINI_API_KEY:}
      timeout-seconds: 60
      confidence-threshold: 0.6

    # 로그 보존
    log-storage:
      retention-days: 7
      max-retention-days: 30
      max-logs-per-container: 10000

    # GHCR 이미지 자동 업데이트 (5.1)
    image-watch:
      enabled: true
      poll-interval-seconds: 300
      ghcr-token: ${GHCR_TOKEN:}
      watches:
        - image: ghcr.io/myorg/myapp
          tag: latest
          container-name-pattern: "myapp-*"

    # Health Check Probe (5.2)
    health-check:
      enabled: true
      probes:
        - container-name-pattern: "web-*"
          liveness:
            type: HTTP
            port: 8080
            path: /health
            initial-delay-seconds: 30
            period-seconds: 10
            failure-threshold: 3
          readiness:
            type: TCP
            port: 8080
            period-seconds: 5
            failure-threshold: 1

    # Desired State 관리 (5.3)
    desired-state:
      enabled: true
      reconcile-interval-seconds: 30
      services:
        - name: web
          image: nginx:latest
          replicas: 3
          container-name-prefix: web

    # 다중 노드 스케줄링 (5.4)
    nodes:
      placement-strategy: LEAST_USED   # LEAST_USED | ROUND_ROBIN
      list:
        - id: node-1
          name: Worker Node 1
          host: 192.168.1.101
          port: 2375
        - id: node-2
          name: Worker Node 2
          host: 192.168.1.102
          port: 2375
```

### 환경 변수 목록

| 환경 변수 | 설명 | 기본값 |
|----------|------|--------|
| `DOCKER_HOST` | Docker 소켓 경로 | `unix:///var/run/docker.sock` |
| `SERVER_NAME` | 서버 식별 이름 | `Production-Server-01` |
| `MAIL_USERNAME` | SMTP 사용자명 | - |
| `MAIL_PASSWORD` | SMTP 비밀번호 | - |
| `ALERT_EMAIL` | 알림 수신 이메일 | - |
| `SECURITY_ENABLED` | JWT 인증 활성화 | `false` |
| `AI_ENABLED` | AI 기능 활성화 | `false` |
| `ANTHROPIC_API_KEY` | Anthropic API 키 | - |
| `OPENAI_API_KEY` | OpenAI API 키 | - |
| `GEMINI_API_KEY` | Google Gemini API 키 | - |
| `GHCR_TOKEN` | GitHub Container Registry 토큰 | - |
| `NODES_ENABLED` | 노드 기능 활성화 | `true` |
| `NODES_HEARTBEAT_INTERVAL` | Heartbeat 주기 (초) | `30` |
| `NODES_PLACEMENT_STRATEGY` | 배치 전략 | `LEAST_USED` |

---

## 대시보드

### 페이지 구성

| 경로 | 설명 |
|------|------|
| `/` | 메인 대시보드 - 컨테이너 목록 및 상태 |
| `/container/{id}` | 컨테이너 상세 정보 |
| `/healing-logs` | 자가치유 이력 조회 |
| `/approvals` | 승인 대기 목록 |
| `/incidents` | 장애 리포트 목록 |
| `/incidents/{id}` | 장애 리포트 상세 |
| `/suggestions` | AI 패턴 제안 목록 |
| `/metrics-history` | 메트릭 히스토리 & 집계 대시보드 |
| `/ai-settings` | AI 프로바이더 및 API 키 설정 |
| `/nodes` | 노드 목록 및 SSH/TCP 등록 |
| `/login` | 로그인 (인증 활성화 시) |

### 실시간 갱신

대시보드는 WebSocket을 통해 15초마다 자동 갱신됩니다. 메트릭 히스토리 페이지는 30초마다 자동 갱신됩니다.

```javascript
// 컨테이너 상태 WebSocket 연결 예시
const ws = new WebSocket('ws://localhost:8080/ws/containers');

ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    // data.type: "initial" 또는 "update"
    // data.containers: 컨테이너 목록
    // data.timestamp: 타임스탬프
    updateDashboard(data.containers);
};
```

---

## 자가치유 (Self-Healing)

### 규칙 기반 설정

```yaml
docker:
  monitor:
    self-healing:
      enabled: true
      reset-window-minutes: 30  # 재시작 횟수 리셋 시간
      rules:
        # 웹 서버: 최대 3회 재시작, 10초 대기
        - name-pattern: "web-*"
          max-restarts: 3
          restart-delay-seconds: 10

        # 워커: 최대 5회 재시작, 5초 대기
        - name-pattern: "worker-*"
          max-restarts: 5
          restart-delay-seconds: 5

        # DB: 최대 1회 재시작, 30초 대기
        - name-pattern: "db-*"
          max-restarts: 1
          restart-delay-seconds: 30
```

### 라벨 기반 설정 (우선순위 높음)

docker-compose.yml에서 컨테이너별로 설정:

```yaml
services:
  web:
    image: nginx
    labels:
      # 자가치유 활성화
      self-healing.enabled: "true"
      self-healing.max-restarts: "3"
      self-healing.restart-delay-seconds: "10"

  database:
    image: postgres
    labels:
      # 자가치유 비활성화
      self-healing.enabled: "false"
```

### 동작 방식

1. 컨테이너 종료 이벤트 감지 (die, oom — kill은 die와 중복이므로 제외)
2. 규칙 매칭 (라벨 → YAML 순서)
3. 재시작 횟수 확인
4. 지연 시간 대기
5. 컨테이너 재시작
6. 실패 시 알림 발송

---

## Playbook 시스템

### Playbook YAML 작성

`src/main/resources/playbooks/` 디렉토리에 YAML 파일 생성:

```yaml
# container-restart.yml
name: container-restart
description: 컨테이너 재시작 Playbook
riskLevel: LOW

trigger:
  event: die
  conditions:
    exitCode: "1"

actions:
  - name: wait-before-restart
    type: delay
    params:
      seconds: "5"

  - name: restart-container
    type: container.restart
    params:
      timeout: "30"
```

### 지원하는 액션 타입

| 타입 | 설명 | 파라미터 |
|------|------|----------|
| `delay` | 대기 | `seconds`: 대기 시간 |
| `container.restart` | 컨테이너 재시작 | `timeout`: 타임아웃 |
| `container.kill` | 컨테이너 강제 종료 | - |
| `notify` | 알림 발송 | `message`: 메시지 |

### OOM Recovery Playbook 예시

```yaml
name: oom-recovery
description: OOM 발생 시 복구
riskLevel: MEDIUM

trigger:
  event: oom
  conditions:
    oomKilled: "true"

actions:
  - name: wait-for-memory-release
    type: delay
    params:
      seconds: "10"

  - name: restart-with-caution
    type: container.restart
    when: "{{restartCount}} < 3"
```

---

## Safety Gate

### 위험도 레벨

| 레벨 | 설명 | 자동 실행 |
|------|------|----------|
| LOW | 낮은 위험 | ✅ 가능 |
| MEDIUM | 중간 위험 | ✅ 가능 |
| HIGH | 높은 위험 | ⚠️ 설정에 따름 |
| CRITICAL | 치명적 | ❌ 항상 수동 |

### 서비스 중요도 설정

```yaml
docker:
  monitor:
    safety-gate:
      # 고위험 조치 자동 차단
      block-high-risk: true

      # 업무 시간 설정 (이 시간에는 위험도 상향)
      business-hours:
        start: "09:00"
        end: "18:00"

      # 서비스 중요도 규칙
      service-criticality:
        rules:
          # 패턴 기반
          - pattern: "db-*"
            criticality: CRITICAL

          - pattern: "api-*"
            criticality: HIGH

          - pattern: "worker-*"
            criticality: NORMAL
```

### 라벨로 서비스 중요도 설정

```yaml
services:
  payment-api:
    image: payment-service
    labels:
      service.criticality: "CRITICAL"
```

---

## Human-in-the-Loop

### 승인 대기 목록 확인

대시보드의 `/approvals` 페이지에서 확인 가능.

### API로 승인/거부

```bash
# 승인 대기 목록 조회
curl http://localhost:8080/api/approvals

# 승인
curl -X POST http://localhost:8080/api/approvals/{id}/approve

# 거부
curl -X POST http://localhost:8080/api/approvals/{id}/reject
```

### 타임아웃

- 승인 대기 요청은 **5분** 후 자동 만료
- 30초마다 만료 체크 실행

---

## Audit Logger

### 감사 로그 조회

```bash
# 전체 로그 조회
curl http://localhost:8080/api/audit-logs

# 컨테이너별 조회
curl http://localhost:8080/api/audit-logs?containerId=abc123

# 날짜 범위 조회
curl "http://localhost:8080/api/audit-logs?from=2024-01-01T00:00:00&to=2024-01-31T23:59:59"
```

### 로그 항목

| 필드 | 설명 |
|------|------|
| `id` | 로그 ID |
| `timestamp` | 기록 시간 |
| `containerId` | 대상 컨테이너 |
| `intent` | 조치 의도 |
| `reasoning` | AI 판단 이유 |
| `action` | 실행된 조치 |
| `result` | 결과 (SUCCESS/FAILURE/BLOCKED) |
| `riskLevel` | 위험도 |

### 보존 정책

- **180일** 보존 (Append-Only)
- 매일 자정 만료 로그 자동 삭제

---

## AI 설정 (멀티 프로바이더)

Anthropic, OpenAI, Gemini 세 가지 AI 프로바이더를 지원합니다. 대시보드에서 런타임으로 전환 가능합니다.

### 대시보드 설정 페이지

`/ai-settings` 페이지에서:
- AI 프로바이더 선택 (Anthropic / OpenAI / Gemini)
- API 키 입력 및 저장
- AI 기능 활성화/비활성화 토글

### 프로바이더별 모델

| 프로바이더 | 모델 | API 엔드포인트 |
|-----------|------|---------------|
| Anthropic | claude-haiku-4-5-20251001 | api.anthropic.com/v1/messages |
| OpenAI | gpt-4o-mini | api.openai.com/v1/chat/completions |
| Gemini | gemini-1.5-flash | generativelanguage.googleapis.com |

### application.yml 설정

```yaml
docker:
  monitor:
    ai:
      enabled: true
      provider: ANTHROPIC          # 기본 프로바이더
      anthropic-api-key: ${ANTHROPIC_API_KEY:}
      openai-api-key: ${OPENAI_API_KEY:}
      gemini-api-key: ${GEMINI_API_KEY:}
      timeout-seconds: 60
      confidence-threshold: 0.6
```

### AI 사후 분석 흐름

AI는 실시간 복구에 관여하지 않고 **사후 분석** 전용으로 동작합니다:

1. 컨테이너 종료 이벤트 발생
2. 규칙 기반 자가치유로 즉시 복구 (AI 없이)
3. (비동기) AI에 컨텍스트 전달 (컨테이너 정보, 메트릭, 로그)
4. AI가 근본 원인 분석 및 재발 방지 제안 생성
5. 장애 리포트 저장 (`/incidents` 페이지)

### API

```bash
# 현재 AI 설정 조회
curl http://localhost:8080/api/ai/settings

# AI 설정 변경 (런타임)
curl -X POST http://localhost:8080/api/ai/settings \
  -H "Content-Type: application/json" \
  -d '{"provider": "OPENAI", "openaiApiKey": "sk-...", "enabled": true}'
```

---

## 로그 수집 및 분석

### 로그 실시간 스트리밍 (WebSocket)

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/logs');

// 컨테이너 구독
ws.send(JSON.stringify({
    action: 'subscribe',
    containerId: 'abc123'
}));

// 로그 수신
ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    // data.type: "log" | "subscribed" | "unsubscribed" | "error"
    // data.containerId: 컨테이너 ID
    // data.content: 로그 내용
    // data.timestamp: 타임스탬프

    if (data.type === 'log') {
        appendLog(data.content);
    }
};

// 구독 해제
ws.send(JSON.stringify({
    action: 'unsubscribe',
    containerId: 'abc123'
}));
```

### 로그 검색 API

```bash
# 키워드 검색
curl "http://localhost:8080/api/containers/{id}/logs?keyword=ERROR"

# 시간 범위 + 레벨 필터
curl "http://localhost:8080/api/containers/{id}/logs?keyword=Exception&levels=ERROR,FATAL&from=2024-01-01T00:00:00"
```

### 로그 보존 설정

```yaml
docker:
  monitor:
    log-storage:
      retention-days: 7        # 보존 기간 (기본 7일)
      max-retention-days: 30   # 최대 보존 기간
      max-logs-per-container: 10000  # 컨테이너당 최대 로그 수
      cleanup-cron: "0 0 2 * * *"    # 정리 스케줄 (매일 새벽 2시)
```

### AI 로그 분석

```bash
# 로그 분석 요청
curl -X POST "http://localhost:8080/api/containers/{id}/analyze-logs" \
  -H "Content-Type: application/json" \
  -d '{
    "logs": "2024-01-01T10:00:00Z ERROR Connection refused\n2024-01-01T10:00:01Z FATAL Shutting down"
  }'
```

응답:
```json
{
    "containerId": "abc123",
    "containerName": "web-server",
    "rootCause": "데이터베이스 연결 실패로 인한 종료",
    "severity": "HIGH",
    "suggestedActions": ["restart", "check-database"],
    "confidence": 0.92,
    "analyzedAt": "2024-01-01T10:05:00"
}
```

---

## 장애 리포트

컨테이너 종료 이벤트가 발생하면 자동으로 장애 리포트가 생성됩니다.

### 페이지

- `/incidents` - 전체 장애 목록 (OPEN / ANALYZING / CLOSED 필터)
- `/incidents/{id}` - 장애 상세 (원인, 타임라인, AI 분석 결과)

### API

```bash
# 장애 목록 조회
curl http://localhost:8080/api/incidents

# 장애 상세 조회
curl http://localhost:8080/api/incidents/{id}

# 장애 종료 처리
curl -X POST http://localhost:8080/api/incidents/{id}/close
```

### 장애 상태

| 상태 | 설명 |
|------|------|
| `OPEN` | 분석 대기 중 |
| `ANALYZING` | AI 분석 진행 중 |
| `CLOSED` | 처리 완료 |

---

## 패턴 감지 및 제안

24시간 이내 동일 컨테이너에서 3회 이상 장애가 반복되면 패턴으로 감지하고 제안을 생성합니다.

### 페이지

- `/suggestions` - 제안 목록 (승인/거부 버튼 포함)

### API

```bash
# 제안 목록 조회
curl http://localhost:8080/api/suggestions

# 제안 승인
curl -X POST http://localhost:8080/api/suggestions/{id}/approve

# 제안 거부
curl -X POST http://localhost:8080/api/suggestions/{id}/reject
```

### 제안 유형

| 유형 | 설명 |
|------|------|
| `CONFIG_OPTIMIZATION` | 메모리 한도, CPU 제한 등 설정 최적화 |
| `PLAYBOOK_IMPROVEMENT` | Playbook 개선 제안 |

---

## 메트릭 히스토리 & 집계

### 페이지: `/metrics-history`

- **게이지 패널**: 현재 컨테이너별 CPU/메모리 실시간 현황 (30초 자동 갱신)
- **추이 차트**: 컨테이너별 시계열 CPU/메모리 차트 (Chart.js 라인 차트)
- **집계 차트**: 시간대별 평균/최대 집계 바차트
- **자가치유 통계**: 전체 횟수, 성공률, 성공/실패 수
- **장애 타임라인**: 최근 7일 인시던트 목록

### 시간 범위 선택

| 옵션 | 설명 |
|------|------|
| Last 24h | 최근 24시간 |
| Last 7 days | 최근 7일 |
| Custom range | datetime-local 피커로 직접 지정 |

### 집계 차트 옵션

| 옵션 | 설명 |
|------|------|
| Average | 시간대별 평균 CPU/메모리 |
| Maximum | 시간대별 최대 CPU/메모리 |

### API

```bash
# 메트릭 히스토리 (시간 기반)
curl "http://localhost:8080/api/metrics-history?container=web-server&hours=24"

# 메트릭 히스토리 (커스텀 날짜 범위)
curl "http://localhost:8080/api/metrics-history/range?container=web-server&from=2026-03-10T00:00:00&to=2026-03-11T00:00:00"

# 멀티 컨테이너 비교
curl "http://localhost:8080/api/metrics-history/compare?containers=web-server,db-server&hours=24"

# CSV 다운로드
curl "http://localhost:8080/api/metrics-history/csv?container=web-server&hours=24" -o metrics.csv

# 현재 게이지 (전체)
curl http://localhost:8080/api/gauges

# 현재 게이지 (단일)
curl http://localhost:8080/api/gauges/{containerId}

# 자동 갱신 주기 조회
curl http://localhost:8080/api/gauges/refresh-interval

# 시간대별 평균 집계
curl "http://localhost:8080/api/metrics-aggregation/hourly-avg?container=web-server&from=2026-03-17T00:00:00&to=2026-03-17T12:00:00"

# 시간대별 최대 집계
curl "http://localhost:8080/api/metrics-aggregation/hourly-max?container=web-server&from=2026-03-17T00:00:00&to=2026-03-17T12:00:00"

# 컨테이너 집계 통계
curl "http://localhost:8080/api/metrics-aggregation/stats?container=web-server&hours=24"
```

### 메트릭 보존 정책

| 항목 | 값 |
|------|-----|
| 기본 보존 기간 | 30일 |
| 만료 삭제 스케줄 | 매일 새벽 3시 |
| 압축 방식 | 30일 초과 데이터를 일별 평균 1개로 압축 |
| 컨테이너당 최대 항목 | 10,000개 |

보존 기간 변경:
```yaml
docker:
  monitor:
    metrics:
      retention:
        retention-days: 14  # 14일로 변경
```

---

## GHCR 이미지 자동 업데이트

GitHub Container Registry (GHCR)의 이미지 digest를 주기적으로 폴링하여 새 버전이 감지되면 자동으로 롤링 업데이트를 수행합니다.

### 설정

```yaml
docker:
  monitor:
    image-watch:
      enabled: true
      poll-interval-seconds: 300    # 폴링 주기 (기본 5분)
      ghcr-token: ${GHCR_TOKEN:}    # 비공개 이미지용 PAT (공개 이미지는 생략)
      watches:
        - image: ghcr.io/myorg/myapp
          tag: latest
          container-name-pattern: "myapp-*"   # 업데이트 대상 컨테이너 패턴
        - image: ghcr.io/myorg/worker
          tag: v2
          container-name-pattern: "worker-*"
```

### 동작 방식

1. `poll-interval-seconds`마다 GHCR Registry v2 API 호출
2. `Docker-Content-Digest` 헤더로 최신 digest 확인
3. 현재 실행 중인 컨테이너의 digest와 비교
4. 변경 감지 시 `ImageUpdateDetectedEvent` 발행
5. `RollingUpdateService`가 이벤트 수신 → maxUnavailable 배치 업데이트 수행

### GitHub PAT 발급 (비공개 이미지)

1. GitHub → Settings → Developer settings → Personal access tokens
2. `read:packages` 권한 부여
3. 환경변수 설정: `export GHCR_TOKEN=ghp_...`

---

## Health Check Probe

Kubernetes의 liveness/readiness probe와 동일한 방식으로 컨테이너 상태를 주기적으로 검사합니다.

### Probe 타입

| 타입 | 설명 |
|------|------|
| `HTTP` | HTTP GET 요청으로 응답 코드 확인 (2xx = 성공) |
| `TCP` | TCP 소켓 연결 가능 여부 확인 |
| `EXEC` | Docker exec로 컨테이너 내부 명령 실행 (exit code 0 = 성공) |

### 설정

```yaml
docker:
  monitor:
    health-check:
      enabled: true
      probes:
        - container-name-pattern: "web-*"
          liveness:
            type: HTTP
            port: 8080
            path: /health
            initial-delay-seconds: 30   # 시작 후 대기 시간
            period-seconds: 10          # 검사 주기
            failure-threshold: 3        # 연속 실패 시 재시작
            timeout-seconds: 5
          readiness:
            type: TCP
            port: 8080
            period-seconds: 5
            failure-threshold: 1

        - container-name-pattern: "db-*"
          liveness:
            type: EXEC
            command: ["pg_isready", "-U", "postgres"]
            period-seconds: 30
            failure-threshold: 3
```

### 동작 방식

- **Liveness 실패**: `failure-threshold` 연속 실패 시 컨테이너 자동 재시작
- **Readiness 실패**: 로그만 기록 (재시작 없음, 트래픽 차단 용도)
- `initial-delay-seconds`: 컨테이너 기동 후 첫 probe 지연 시간

---

## Desired State 관리

선언적으로 서비스의 목표 상태(replicas 수, 이미지 등)를 정의하면 시스템이 실제 상태를 목표 상태에 맞게 자동으로 유지합니다.

### 설정

```yaml
docker:
  monitor:
    desired-state:
      enabled: true
      reconcile-interval-seconds: 30   # 조정 주기
      services:
        - name: web
          image: nginx:latest
          replicas: 3
          container-name-prefix: web   # 컨테이너 이름: web-0, web-1, web-2
          env:
            - "ENV=production"
          ports:
            - "80:80"
          labels:
            app: web

        - name: worker
          image: myworker:latest
          replicas: 5
          container-name-prefix: worker
```

### 동작 방식

| 상황 | 조치 |
|------|------|
| 실행 중 컨테이너 수 < replicas | 부족한 수만큼 새 컨테이너 생성 |
| 실행 중 컨테이너 수 > replicas | 초과된 컨테이너 중지 및 제거 |
| 죽은(exited) 컨테이너 존재 | 자동 정리 |

### CrashLoop 백오프

컨테이너가 반복 재시작될 경우 지수 백오프(Exponential Backoff) 적용:

| 실패 횟수 | 대기 시간 |
|----------|----------|
| 1회 | 10초 |
| 2회 | 20초 |
| 3회 | 40초 |
| 4회 이상 | 최대 300초 (5분) |

---

## 다중 노드 스케줄링

여러 Docker 호스트를 등록하고 컨테이너를 최적의 노드에 배포합니다. 노드 장애 시 다른 노드로 자동 마이그레이션합니다.

### 설정

```yaml
docker:
  monitor:
    nodes:
      placement-strategy: LEAST_USED   # LEAST_USED | ROUND_ROBIN
      list:
        - id: node-1
          name: Worker Node 1
          host: 192.168.1.101
          port: 2375
        - id: node-2
          name: Worker Node 2
          host: 192.168.1.102
          port: 2375
        - id: node-3
          name: Worker Node 3
          host: 192.168.1.103
          port: 2375
```

### 배치 전략

| 전략 | 설명 |
|------|------|
| `LEAST_USED` | CPU + 메모리 사용률이 가장 낮은 노드 선택 |
| `ROUND_ROBIN` | 노드를 순서대로 순환 배치 |

### 노드 장애 감지 및 마이그레이션

- 30초마다 각 노드에 heartbeat 확인 (Docker API ping)
- HEALTHY → UNHEALTHY 전환 시 `NodeFailureEvent` 발행
- `ContainerMigrator`가 장애 노드의 컨테이너를 건강한 노드로 자동 이전

### Docker 원격 API 활성화 (각 노드)

```bash
# /etc/docker/daemon.json
{
  "hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:2375"]
}
```

> 프로덕션에서는 TLS 인증을 반드시 활성화하세요.

---

## 멀티 노드 통합 모니터링 (Phase 6)

### 개요

원격 VM의 Docker 호스트를 등록하면 로컬과 동일하게 컨테이너 모니터링, 메트릭 수집, 자가치유, 헬스체크가 동작합니다.

- TCP 직접 연결 (GCP 내부 IP 등)
- SSH 터널 연결 (온프레미스, 외부망 서버)

### application.yml 설정

```yaml
docker:
  monitor:
    nodes:
      enabled: true
      heartbeat-interval-seconds: 30
      placement-strategy: LEAST_USED   # LEAST_USED | ROUND_ROBIN
      nodes:
        # GCP 내부망 — TCP 직접 연결
        - name: gcp-vm-1
          host: 10.178.0.15
          port: 2375
          connection-type: TCP

        # 온프레미스 — SSH 터널 경유
        - name: onprem-dev
          host: 183.102.124.134
          port: 2375
          connection-type: SSH
          ssh-port: 22
          ssh-user: ubuntu
          ssh-key-path: /root/.ssh/id_rsa  # 서버(컨테이너) 내부 경로
```

### 환경변수 방식 (docker-compose)

```env
NODES_ENABLED=true
DOCKER_MONITOR_NODES_NODES_0_NAME=gcp-vm-1
DOCKER_MONITOR_NODES_NODES_0_HOST=10.178.0.15
DOCKER_MONITOR_NODES_NODES_0_PORT=2375
DOCKER_MONITOR_NODES_NODES_0_CONNECTION_TYPE=TCP

DOCKER_MONITOR_NODES_NODES_1_NAME=onprem-dev
DOCKER_MONITOR_NODES_NODES_1_HOST=183.102.124.134
DOCKER_MONITOR_NODES_NODES_1_PORT=2375
DOCKER_MONITOR_NODES_NODES_1_CONNECTION_TYPE=SSH
DOCKER_MONITOR_NODES_NODES_1_SSH_PORT=22
DOCKER_MONITOR_NODES_NODES_1_SSH_USER=ubuntu
DOCKER_MONITOR_NODES_NODES_1_SSH_KEY_PATH=/root/.ssh/id_rsa
```

### SSH 터널 사전 조건

1. docker-monitor 컨테이너에 SSH 비밀키 마운트:
```yaml
volumes:
  - ~/.ssh/id_rsa:/root/.ssh/id_rsa:ro
```
2. 대상 VM의 `~/.ssh/authorized_keys`에 공개키 등록
3. 대상 VM의 Docker 소켓 접근 가능 (`/var/run/docker.sock`)
4. 대상 VM에서 Docker TCP 오픈 불필요 — SSH만 열려 있으면 됨

### SSH 터널 동작 구조

```
docker-monitor 서버
  │ JSch SSH 터널
  └── ssh ubuntu@183.102.124.134:22
        localhost:12375 → /var/run/docker.sock

NodeDockerClientFactory → tcp://localhost:12375 (터널 경유)
```

### 런타임 노드 등록 (UI / API)

**대시보드 `/nodes` 페이지:**
- 연결 방식 선택 드롭다운 (TCP / SSH 터널)
- SSH 선택 시 SSH 포트, SSH 유저, SSH 키 경로 입력 필드 노출
- 노드 카드에 TCP(파란 배지) / SSH(보라 배지) 표시

**API:**
```bash
# TCP 노드 추가
curl -X POST http://localhost:8080/api/nodes \
  -H "Content-Type: application/json" \
  -d '{"name":"gcp-vm-1","host":"10.178.0.15","port":2375,"connectionType":"TCP"}'

# SSH 터널 노드 추가
curl -X POST http://localhost:8080/api/nodes \
  -H "Content-Type: application/json" \
  -d '{"name":"onprem-dev","host":"183.102.124.134","port":2375,"connectionType":"SSH","sshPort":22,"sshUser":"ubuntu","sshKeyPath":"/root/.ssh/id_rsa"}'

# 노드 목록
curl http://localhost:8080/api/nodes

# 노드 제거
curl -X DELETE http://localhost:8080/api/nodes/{id}
```

### 원격 노드 컨테이너 모니터링

노드 등록 후 자동으로:
- 컨테이너 탭에 원격 컨테이너 표시 (Node 컬럼에 배지)
- 원격 컨테이너 상세 조회 및 로그 조회 지원
- 원격 컨테이너 CPU/메모리 메트릭 15초마다 수집
- Heartbeat(30초)마다 노드 전체 CPU/메모리 집계 업데이트
- 노드 장애 시 `NodeFailureEvent` 발행 → 컨테이너 자동 마이그레이션

### 배치 전략

| 전략 | 설명 |
|------|------|
| `LEAST_USED` | 컨테이너 stats 합산 CPU+메모리가 가장 낮은 노드 선택 |
| `ROUND_ROBIN` | 노드를 순서대로 순환 배치 |

### 노드가 UNHEALTHY로 감지되는 경우

TCP 노드:
```bash
# Docker 원격 API 활성화 필요
# /etc/docker/daemon.json
{"hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:2375"]}
```

SSH 노드:
- SSH 연결 실패 시 UNHEALTHY 마크
- 비밀키 경로 및 authorized_keys 확인

---

## 배포 전략

4가지 배포 전략을 지원합니다. `DeploymentSpec`을 구성하여 프로그래매틱하게 사용하거나, 향후 API/대시보드에서 트리거할 수 있습니다.

### Rolling Update

maxUnavailable 단위로 배치 교체합니다. 다운타임 없이 점진적 배포.

```
기존: [A, A, A, A, A]
→ 배치 1: [B, A, A, A, A] (A 중지 → B 생성)
→ 배치 2: [B, B, A, A, A]
→ 완료: [B, B, B, B, B]
```

| 파라미터 | 설명 | 기본값 |
|---------|------|--------|
| `maxUnavailable` | 동시 교체 컨테이너 수 | 1 |

### Recreate

모든 컨테이너를 중지한 후 새 버전으로 일괄 재생성. 다운타임 발생, 가장 단순.

```
기존: [A, A, A] → 전체 중지 → [B, B, B]
```

### Blue-Green

Green 컨테이너를 모두 생성하고 정상 확인 후 Blue를 제거. 빠른 롤백 가능.

```
Blue: [A, A, A] (기존)
→ Green 생성: [A, A, A] + [B-green, B-green, B-green]
→ Green 정상 확인 (3초 대기)
→ Blue 제거: [B-green, B-green, B-green]
→ 실패 시: Green 제거 → Blue 유지 (롤백)
```

### Canary

weight% 만큼의 canary 컨테이너를 기존 컨테이너 옆에 추가 배포. 기존 컨테이너는 유지.

```
기존: [A, A, A, A, A] (5개, weight=20%)
→ ceil(5 * 20 / 100) = 1개 canary 추가
→ 결과: [A, A, A, A, A, A-canary]
```

| 파라미터 | 설명 | 기본값 |
|---------|------|--------|
| `canaryWeight` | canary 비율 (%) | 20 |

### 전략 비교

| 전략 | 다운타임 | 롤백 속도 | 리소스 | 복잡도 |
|------|---------|----------|--------|--------|
| Rolling Update | 없음 | 느림 | 동일 | 낮음 |
| Recreate | 있음 | 빠름 | 동일 | 최저 |
| Blue-Green | 없음 | 즉시 | 2배 | 중간 |
| Canary | 없음 | 즉시 | +일부 | 높음 |

---

## 알림 설정

### 이메일 알림 종류

| 알림 유형 | 트리거 |
|----------|--------|
| 컨테이너 종료 | die, oom 이벤트 |
| CPU 임계치 초과 | CPU 사용률 > 80% |
| 메모리 임계치 초과 | 메모리 사용률 > 90% |
| 재시작 반복 | 5분 내 3회 이상 재시작 |
| 자가치유 실패 | 재시작 실패 |
| 최대 재시작 초과 | 최대 횟수 도달 |
| 노드 장애 | 노드 UNHEALTHY 전환 |

### 알림 중복 방지

```yaml
docker:
  monitor:
    deduplication:
      enabled: true
      window-seconds: 60  # 60초 내 동일 알림 차단
```

### 다중 수신자

```yaml
docker:
  monitor:
    notification:
      email:
        to: admin@example.com,ops@example.com,oncall@example.com
```

---

## API 레퍼런스

### 컨테이너

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/containers` | 컨테이너 목록 |
| GET | `/api/containers/{id}` | 컨테이너 상세 |
| GET | `/api/containers/{id}/logs` | 로그 조회 |
| POST | `/api/containers/{id}/restart` | 재시작 |

### 자가치유

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/healing-logs` | 자가치유 이력 |
| GET | `/api/healing-logs?status=SUCCESS` | 상태별 필터 |

### 승인

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/approvals` | 승인 대기 목록 |
| GET | `/api/approvals/pending` | 대기 중만 |
| POST | `/api/approvals/{id}/approve` | 승인 |
| POST | `/api/approvals/{id}/reject` | 거부 |

### 감사 로그

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/audit-logs` | 감사 로그 조회 |
| GET | `/api/audit-logs/stats` | 통계 |

### 장애 리포트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/incidents` | 장애 목록 |
| GET | `/api/incidents/{id}` | 장애 상세 |
| POST | `/api/incidents/{id}/close` | 장애 종료 |

### 패턴 제안

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/suggestions` | 제안 목록 |
| POST | `/api/suggestions/{id}/approve` | 제안 승인 |
| POST | `/api/suggestions/{id}/reject` | 제안 거부 |

### 메트릭 히스토리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/metrics-history` | 시간 기반 히스토리 |
| GET | `/api/metrics-history/range` | 커스텀 날짜 범위 |
| GET | `/api/metrics-history/compare` | 멀티 컨테이너 비교 |
| GET | `/api/metrics-history/csv` | CSV 다운로드 |

### 게이지 (현재 상태)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/gauges` | 전체 컨테이너 게이지 |
| GET | `/api/gauges/{containerId}` | 단일 컨테이너 게이지 |
| GET | `/api/gauges/refresh-interval` | 자동 갱신 주기 (초) |

### 메트릭 집계

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/metrics-aggregation/hourly-avg` | 시간대별 평균 |
| GET | `/api/metrics-aggregation/hourly-max` | 시간대별 최대 |
| GET | `/api/metrics-aggregation/stats` | 컨테이너 전체 통계 |

### AI 설정

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/ai/settings` | 현재 AI 설정 조회 |
| POST | `/api/ai/settings` | AI 설정 변경 (런타임) |

### 노드

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/nodes` | 노드 목록 및 상태 |
| POST | `/api/nodes` | 노드 추가 (TCP/SSH) |
| DELETE | `/api/nodes/{id}` | 노드 제거 |

---

## WebSocket 사용법

### 컨테이너 상태 스트리밍

**엔드포인트**: `ws://localhost:8080/ws/containers`

**수신 메시지 형식**:
```json
{
    "type": "initial|update",
    "containers": [...],
    "timestamp": 1704067200000
}
```

**새로고침 요청**:
```javascript
ws.send('refresh');
```

### 로그 스트리밍

**엔드포인트**: `ws://localhost:8080/ws/logs`

**구독 요청**:
```json
{"action": "subscribe", "containerId": "abc123"}
```

**구독 해제**:
```json
{"action": "unsubscribe", "containerId": "abc123"}
```

**수신 메시지**:
```json
{
    "type": "log|subscribed|unsubscribed|error",
    "containerId": "abc123",
    "content": "로그 내용",
    "timestamp": "2024-01-01T10:00:00"
}
```

---

## 트러블슈팅

### Docker 연결 실패

```
Connection refused: /var/run/docker.sock
```

**해결**:
- Docker Desktop 실행 확인
- 소켓 권한 확인: `ls -la /var/run/docker.sock`
- Mac: Docker Desktop → Settings → Advanced → "Allow the default Docker socket"

### AI 기능이 동작하지 않음

1. AI 활성화 및 프로바이더 확인:
```yaml
docker.monitor.ai.enabled: true
docker.monitor.ai.provider: ANTHROPIC
```

2. API 키 확인:
```bash
echo $ANTHROPIC_API_KEY   # 또는 OPENAI_API_KEY / GEMINI_API_KEY
```

3. `/ai-settings` 대시보드에서 런타임 설정 변경 가능

4. 타임아웃 확인:
```yaml
docker.monitor.ai.timeout-seconds: 120  # 늘려보기
```

### GHCR 이미지 업데이트가 감지되지 않음

1. `image-watch.enabled: true` 확인
2. 비공개 이미지의 경우 `GHCR_TOKEN` 환경변수 설정
3. `image` 필드가 `ghcr.io/org/repo` 형식인지 확인 (앞의 `ghcr.io/` 포함)

### Health Check로 컨테이너가 계속 재시작됨

1. `initial-delay-seconds` 값을 늘려 기동 시간 확보
2. `failure-threshold` 값을 높여 일시적 오류 허용
3. probe 타입과 포트/경로가 실제 애플리케이션과 일치하는지 확인

### 노드가 UNHEALTHY로 감지됨

**TCP 노드:**
1. 원격 노드의 Docker API가 열려 있는지 확인:
```bash
curl http://{node-host}:2375/version
```
2. 방화벽에서 2375 포트 허용 확인
3. `NodeProperties`의 host/port 설정 확인

**SSH 노드:**
1. SSH 연결 가능 여부 확인:
```bash
ssh -i /path/to/id_rsa ubuntu@{node-host}
```
2. docker-monitor 컨테이너에 SSH 키 마운트 확인 (`~/.ssh/id_rsa:/root/.ssh/id_rsa:ro`)
3. 대상 VM의 `~/.ssh/authorized_keys`에 공개키 등록 확인
4. `sshUser`, `sshPort`, `sshKeyPath` 설정 확인

### 자가치유가 동작하지 않음

1. 활성화 확인:
```yaml
docker.monitor.self-healing.enabled: true
```

2. 규칙 패턴 확인 (와일드카드 `*` 사용)

3. 라벨 확인:
```bash
docker inspect {container} | grep -A 10 Labels
```

4. 최대 재시작 횟수 도달 여부 확인

### 알림이 오지 않음

1. SMTP 설정 확인
2. Gmail 앱 비밀번호 사용 (일반 비밀번호 X)
3. 중복 방지로 차단되었는지 확인 (60초 내 동일 알림)
4. 방화벽 SMTP 포트 (587, 465) 확인

### 메모리 사용량 증가

로그 보존 설정 확인:
```yaml
docker.monitor.log-storage:
  max-logs-per-container: 5000  # 줄이기
  retention-days: 3              # 줄이기
```

메트릭 보존 기간 조정:
```yaml
docker.monitor.metrics.retention:
  retention-days: 7  # 줄이기
```

---

## 버전 정보

- **현재 버전**: 1.0.0
- **테스트 케이스**: 460개
- **구현 진행률**: 100% (Phase 1~6 완료)

### 완료된 Phase

- ✅ Phase 1: Foundation (기본 모니터링 + 알림) - 26/26
- ✅ Phase 2: 규칙 기반 자가치유 엔진 - 42/42
- ✅ Phase 3: AI 사후 분석 시스템 - 21/21
- ✅ Phase 4: 모니터링 대시보드 고도화 - 11/11
- ✅ Phase 5: K8s-like 오케스트레이션 - 85/85
  - 5.1 GHCR 이미지 자동 업데이트
  - 5.2 Health Check Probe (HTTP/TCP/EXEC)
  - 5.3 Desired State 관리 (선언적 replicas)
  - 5.4 다중 노드 스케줄링 + 자동 마이그레이션
  - 5.5 배포 전략 4종 (RollingUpdate/Recreate/BlueGreen/Canary)
- ✅ AI 멀티 프로바이더 (Anthropic / OpenAI / Gemini)
- ✅ Phase 6: 멀티 노드 통합 모니터링
  - 원격 노드 컨테이너 통합 표시 (로컬 + 원격)
  - 원격 노드 컨테이너 메트릭 수집
  - SSH 터널 연결 (JSch 기반)
  - 런타임 노드 등록 (UI/API, TCP/SSH)
  - TCP/SSH 배지 표시

### 예정된 Phase

- Phase 7: GCP 통합
