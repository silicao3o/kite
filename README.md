# Docker Container Monitor

Docker 컨테이너가 비정상 종료되었을 때 이메일 알림을 보내고, K8s 스타일의 자가치유(Self-Healing)를 수행하는 Spring Boot 기반 모니터링 서비스입니다.

## 주요 기능

### 모니터링
- **실시간 컨테이너 감시**: Docker Events API를 통한 실시간 이벤트 스트리밍
- **리소스 메트릭 수집**: CPU, 메모리, 네트워크 I/O 주기적 수집 (기본 15초)
- **종료 이벤트 감지**: `die`, `oom` 이벤트 자동 감지 (kill은 die와 중복이므로 제외)
- **Exit Code 분석**: 종료 코드별 원인 분석 및 해결 방안 제시
- **OOM Killer 감지**: 메모리 부족으로 인한 강제 종료 별도 표시
- **마지막 로그 수집**: 종료 직전 컨테이너 로그 자동 수집
- **로그 검색**: 키워드 검색, 레벨 필터, 시간 범위 필터 (1h/6h/24h 프리셋 지원), 검색 결과 하이라이트, 스레드별 필터링, traceId(workflowId) 기반 요청 추적, 멀티노드 검색 지원, 검색 결과 타임스탬프/라인번호 표시

### 자가치유 (Self-Healing)
- **K8s 스타일 자동 재시작**: 규칙 기반 컨테이너 자동 재시작
- **와일드카드 패턴 매칭**: `web-*`, `worker-*` 등 패턴으로 규칙 적용
- **최대 재시작 횟수 제한**: 무한 재시작 방지
- **재시작 지연 시간**: 설정된 시간만큼 대기 후 재시작
- **시간 윈도우 기반 리셋**: 일정 시간 후 재시작 횟수 초기화
- **라벨 기반 설정**: 컨테이너 라벨로 개별 설정 (yml 설정보다 우선)
- **DB 기반 동적 규칙 관리 (Phase 7.12)**: 재시작 없이 REST API로 자가치유 규칙 추가/수정/삭제
  - YAML 규칙 + DB 규칙 합산 (namePattern 충돌 시 DB 우선)
  - `POST /api/self-healing/rules` — 규칙 추가 (namePattern, maxRestarts, restartDelaySeconds, nodeName)
  - `GET /api/self-healing/rules` — 목록 조회
  - `PUT /api/self-healing/rules/{id}` — 수정
  - `DELETE /api/self-healing/rules/{id}` — soft delete (enabled=false)
- **의도적 종료 분류 (Phase 7.14)**: 사용자의 `docker stop`/`docker kill`은 자가치유·알림 모두 스킵
  - Docker 이벤트 스트림의 `stop`/`kill` 선행 이벤트로 판정 (exit code 단독은 근거로 쓰지 않음 — STOPSIGNAL 커스텀 이미지 대응)
  - `OwnActionTracker`로 자체 API 호출(restart/recreate/reconcile 등)을 마킹하여 사용자 명령과 구분
  - 라벨 `self-healing.heal-intentional=true`로 override 가능 (중요 서비스용)

### 이메일 구독 기반 알림 라우팅 (Phase 7.17)
- **수신자별 구독**: 각 이메일 주소가 자신이 관심 있는 컨테이너/노드를 DB에 등록, 해당 이벤트만 수신
- **매칭 방식**:
  - `containerPattern` (와일드카드 `*` 지원): 매칭 컨테이너 이벤트 수신
  - `nodeName` (정확 매칭): 해당 노드의 모든 컨테이너 이벤트 수신
  - 둘 다 지정 시 AND 조건 (해당 노드의 해당 패턴만)
- **글로벌 관리자**: application.yml의 `email.to`는 항상 모든 알림 수신
- **의도적 종료 처리**: 구독별 `notifyIntentional` 플래그로 개별 제어
- **개별 발송**: 각 수신자에게 setTo 단일 MimeMessage로 개별 전송 (중복 이메일 제거)
- **API**:
  - `POST /api/email-subscriptions` — 구독 추가
  - `GET /api/email-subscriptions` — 목록 조회
  - `PUT /api/email-subscriptions/{id}` — 수정
  - `DELETE /api/email-subscriptions/{id}` — soft delete
- **UI**: `/email-subscriptions` (이메일별 그룹핑 테이블)

### 알림
- **이메일 알림**: HTML 형식의 상세한 알림 이메일 발송
- **알림 중복 방지**: 동일 이벤트에 대한 중복 알림 차단
- **CPU/메모리 임계치 알림**: CPU 80%, 메모리 90% 초과 시 알림 (설정 가능)
- **Crash Loop 자동 차단**: 5분 내 3회 이상 재시작 시 컨테이너 강제 정지 + 전용 이메일 알림 (DB 스키마 불일치 등 반복 실패 감지)
- **자가치유 실패 알림**: 재시작 실패 시 이메일 알림
- **최대 재시작 초과 알림**: 최대 횟수 도달 시 이메일 알림

### 대시보드
- **웹 대시보드**: 컨테이너 상태 실시간 모니터링
- **WebSocket 실시간 갱신**: 컨테이너 상태 자동 갱신 (15초 주기)
- **노드별 필터링**: 컨테이너 목록을 노드별로 필터링 (All Nodes / local / 원격 노드)
- **JWT 인증**: 선택적 JWT 기반 인증 (기본 비활성화)
- **자가치유 상태 표시**: 컨테이너별 자가치유 설정 및 재시작 횟수 표시
- **자가치유 로그 뷰어**: 자가치유 이력 조회 (성공/실패 필터링 지원)
- **컨테이너 상세**: 개별 컨테이너의 자가치유 이력 표시

### Playbook 시스템
- **YAML 기반 Playbook**: 이상 이벤트 발생 시 실행할 액션 시퀀스 정의
- **이벤트 매칭**: 이벤트 타입(die, oom 등)과 조건(exitCode, oomKilled 등)으로 Playbook 매칭
- **위험도 레벨**: LOW/MEDIUM/HIGH/CRITICAL 레벨로 조치 위험도 구분
- **기본 Playbook**: container-restart, oom-recovery, cpu-throttle, disk-full
- **확장 가능한 액션**: ContainerRestartHandler, DelayHandler 등

### Safety Gate
- **위험도 산정**: 액션 타입별 위험도 자동 분류
- **서비스 중요도**: 패턴/라벨 기반 서비스 중요도 설정 (LOW/NORMAL/HIGH/CRITICAL)
- **위험도 상향 조정**: 중요 서비스에 대한 조치는 위험도 자동 상향
- **시간대별 위험도 가중치**: 업무 시간 내 조치는 위험도 한 단계 상향
- **고위험 조치 자동 차단**: HIGH 위험도 조치 자동 차단 옵션
- **Critical 조치 항상 수동**: CRITICAL 위험도 조치는 항상 자동 실행 차단

### Human-in-the-Loop
- **승인 대기 목록**: 고위험 조치에 대한 수동 승인 요청
- **대시보드 승인 UI**: /approvals 페이지에서 승인/거부 처리
- **5분 타임아웃**: 승인 대기 요청은 5분 후 자동 만료
- **실시간 알림**: 대기 중인 승인 개수 표시

### Audit Logger
- **감사 로그**: 모든 AI 조치의 의도/실행/결과 기록
- **AI 판단 이유**: reasoning 필드에 AI 판단 근거 저장
- **통계**: 성공률, 차단 횟수 등 조치 통계 제공
- **Append-Only**: 저장만 가능, 수정/삭제 불가 (보존 정책 제외)
- **180일 보존**: 매일 자정 만료 로그 자동 삭제

### AI 멀티 프로바이더
- **3종 AI 지원**: Anthropic (claude-haiku), OpenAI (gpt-4o-mini), Gemini (gemini-1.5-flash)
- **런타임 전환**: `/ai-settings` 대시보드에서 프로바이더 및 API 키를 실시간으로 변경
- **이상 컨텍스트 전달**: 컨테이너 정보, 메트릭, 로그를 AI에게 전달
- **응답 파싱**: JSON/텍스트 형식의 AI 응답 파싱
- **신뢰도 기반 필터링**: 신뢰도 임계값 미만 판단 제외
- **기본 프로바이더**: Anthropic (환경변수 `ANTHROPIC_API_KEY`)

### 로그 수집
- **실시간 로그 스트리밍**: WebSocket으로 컨테이너 로그 실시간 전송 (/ws/logs)
- **로그 보존**: 7일 기본, 최대 30일 설정 가능
- **자동 정리**: 보존 기간 초과 로그 매일 자동 삭제
- **컨테이너별 저장**: 컨테이너 단위로 로그 저장 및 조회

### 장애 리포트 (Incident Reports)
- **자동 리포트 생성**: 컨테이너 종료 이벤트 발생 시 장애 리포트 자동 생성
- **AI 사후 분석**: Claude Code CLI를 통한 근본 원인(Root Cause) 분석 (AI 활성화 시)
- **재발 방지 제안**: AI가 제안하는 재발 방지 조치 저장
- **리포트 대시보드**: /incidents 페이지에서 전체 장애 이력 조회
- **상세 페이지**: /incidents/{id}에서 개별 장애의 원인, 타임라인, 제안 사항 확인
- **상태 관리**: OPEN → ANALYZING → CLOSED 상태 추적

### 메트릭 히스토리 (Metrics History)
- **실시간 기록**: 15초마다 수집된 컨테이너 메트릭을 자동으로 히스토리에 저장
- **추이 차트**: /metrics-history 페이지에서 CPU/메모리 사용률 추이 차트 (Chart.js)
- **기간 선택**: 최근 24시간 / 7일 범위 선택
- **자가치유 통계**: 전체 횟수, 성공률, 컨테이너별 집계
- **장애 타임라인**: 최근 7일간 장애 이벤트 타임라인
- **CSV 내보내기**: 컨테이너별 메트릭 이력 CSV 다운로드 (/api/metrics-history/csv)

### 패턴 감지 및 제안 (Suggestions)
- **반복 장애 패턴 감지**: 24시간 내 동일 컨테이너 3회 이상 장애 시 패턴으로 감지
- **설정 최적화 제안**: 패턴 기반 메모리/CPU 한도 등 설정 최적화 제안 자동 생성
- **Playbook 개선 제안**: 반복 장애 시 관련 Playbook 개선 방안 제안
- **제안 승인/거부 UI**: /suggestions 페이지에서 제안 목록 조회 및 승인/거부 처리
- **AI 제안**: Claude Code CLI 활성화 시 AI 기반 맞춤 제안 생성

### AI 로그 분석
- **로그 기반 원인 분석**: AI가 로그를 분석하여 근본 원인 식별
- **심각도 판정**: LOW/MEDIUM/HIGH/CRITICAL 심각도 자동 분류
- **권장 조치**: 분석 결과에 따른 권장 조치 제안
- **신뢰도 점수**: AI 분석 신뢰도 제공

### GHCR 이미지 자동 업데이트 (Phase 5.1)
- **Digest 폴링**: GHCR Registry v2 API로 이미지 digest 주기적 확인
- **변경 감지**: 현재 컨테이너 digest와 최신 digest 비교
- **자동 롤링 업데이트**: 변경 감지 시 maxUnavailable 배치 단위로 업데이트
- **공개/비공개 이미지**: PAT 토큰으로 비공개 이미지 지원
- **이미지 와치 DB 관리**: REST API + 웹 UI로 와치 CRUD, 업데이트 이력(DETECTED/SUCCESS/FAILED) 저장
- **다중 노드 지정**: 와치별로 대상 노드 목록 지정 가능 (빈 리스트면 전체 노드)
- **와치별 독립 폴링**: 와치마다 독립적인 폴링 주기로 자동 감시 (글로벌 주기 없이 개별 스케줄링)
- **수동 트리거**: API/UI에서 개별 또는 전체 와치를 즉시 트리거하여 업데이트 체크

### Health Check Probe (Phase 5.2)
- **3종 Probe**: HTTP, TCP, EXEC 방식 지원
- **Liveness Probe**: 실패 시 컨테이너 자동 재시작
- **Readiness Probe**: 실패 시 로그 기록 (재시작 없음)
- **초기 지연**: `initial-delay-seconds`로 기동 시간 확보
- **임계값**: `failure-threshold` 연속 실패 횟수로 판정

### Desired State 관리 (Phase 5.3 + 7.11)
- **선언적 replicas**: 목표 컨테이너 수를 선언하면 자동 유지
- **부족 시 생성**: 실행 중 수 < replicas → 차이만큼 신규 생성
- **초과 시 제거**: 실행 중 수 > replicas → 초과분 중지·제거
- **CrashLoop 백오프**: 반복 재시작 시 지수 백오프 (10s→20s→40s→300s 상한)
- **죽은 컨테이너 정리**: exited 상태 컨테이너 자동 제거
- **DB 기반 동적 관리**: `/desired-state` 페이지 및 REST API로 재시작 없이 서비스 스펙 추가/수정/삭제
  - YAML 스펙 + DB 스펙 합산 (이름 충돌 시 DB 우선)
  - `POST /api/desired-state/services` — 서비스 스펙 추가
  - `GET /api/desired-state/services` — 목록 조회
  - `PUT /api/desired-state/services/{id}` — 수정 (replicas, image, nodeName)
  - `DELETE /api/desired-state/services/{id}` — soft delete (enabled=false)

### 다중 노드 스케줄링 (Phase 5.4)
- **노드 등록**: SSH 터널 / SSH_PROXY(CP 경유) 연결 지원 (TCP 직접 연결 제거 — 보안)
- **배치 전략**: LeastUsed (CPU+메모리 최소) / RoundRobin
- **Heartbeat 모니터링**: 30초마다 노드 상태 확인
- **자동 마이그레이션**: 노드 장애 시 다른 노드로 컨테이너 자동 이전
- **장애 이벤트**: NodeFailureEvent → ContainerMigrator 연동
- **SSH 터널**: JSch 기반 Unix 소켓 포워딩 — Docker 데몬 TCP 설정 불필요, SSH 22포트만 개방
- **패스프레이즈 지원**: 암호화된 SSH 키 사용 가능 (UI에서 직접 입력)
- **원격 메트릭 수집**: 원격 노드 컨테이너 CPU/메모리 15초마다 수집
- **UI 노드 등록**: /nodes 페이지에서 SSH/SSH_PROXY 노드 동적 등록

### SSH_PROXY — CP 경유 연결 (Phase 7.1-C)
- **2종 연결 방식**: SSH(직접 터널) / SSH_PROXY(CP 점프 호스트 경유)
- **CP 세션 공유**: SSH_PROXY 노드가 여러 개여도 CP SSH 세션은 1개만 유지
- **포트포워딩 룰**: VM 노드마다 CP 세션에 룰 추가 (`setPortForwardingL`), 세션 재생성 없음
- **자동 재연결**: CP 세션 끊김 감지 시 자동 재연결 후 포워딩 재등록
- **선택적 연결 해제**: 마지막 SSH_PROXY 노드 제거 시에만 CP 세션 disconnect

### 배포 전략 (Phase 5.5)
- **Rolling Update**: maxUnavailable 배치 단위 순차 교체, 무중단
- **Recreate**: 전체 중지 후 일괄 재생성, 가장 단순
- **Blue-Green**: Green 생성 → 확인 → Blue 제거, 즉시 롤백 가능
- **Canary**: weight% 만큼 canary 컨테이너를 기존 옆에 추가

### Service Definitions — Compose 관리 + Docker API 배포
- **Compose YAML 관리**: DB에 Compose YAML 저장, REST API + 웹 UI로 CRUD
- **Docker API 배포**: Compose YAML 파싱 → Docker createContainer + start (docker-compose CLI 불필요)
- **Env Profile 연동**: 배포 시 Env Profile의 환경변수를 자동 주입, `${KEY:-default}` 변수 치환
- **Compose Profiles 지원**: `profiles` 필드 파싱, 배포 시 `activeProfiles` 파라미터로 서비스 필터링
- **네트워크 자동 생성**: compose networks 정의 기반, suffix 매칭으로 기존 네트워크 재사용
- **라이프사이클 관리**: DRAFT → DEPLOYED → STOPPED 상태 관리, stop/redeploy API
- **GHCR 인증 pull**: private 이미지 레지스트리 토큰으로 인증하여 pull

### 멀티 노드 통합 모니터링 (Phase 6)
- **원격 컨테이너 통합 표시**: 로컬과 원격 노드 컨테이너를 하나의 대시보드에 표시
- **원격 메트릭 수집**: 원격 노드 컨테이너 CPU/메모리 15초마다 수집
- **노드 리소스 모니터링**: Heartbeat 시 컨테이너 stats 합산으로 노드 CPU/메모리 추정
- **SSH 터널 연결**: JSch 기반 SSH 터널로 온프레미스 서버 연결 (TCP 오픈 불필요)
- **SSH_PROXY 연결**: CP(점프 호스트) 경유로 GCP VM·온프레미스 동시 접근 — CP 세션 1개 공유
- **런타임 노드 등록**: /nodes 페이지 UI 및 REST API로 TCP/SSH/SSH_PROXY 노드 동적 등록
- **TCP/SSH 배지**: 노드 카드에 연결 방식 시각적 표시

### 이미지 업데이트 & 컨테이너 관리
- **최신 이미지 Pull**: 컨테이너 상세 탭의 **Update Image** 버튼으로 최신 이미지를 즉시 Pull
- **무설정 재생성**: compose 파일 없이도 기존 컨테이너 설정(env, ports, volumes, labels)을 그대로 읽어 재생성
- **자동 교체 흐름**: pull → stop → remove → create → start 순서로 자동 실행
- **ghcr.io 인증**: `GHCR_TOKEN` 환경변수로 GHCR 이미지 pull 인증 지원 (linux/amd64 플랫폼 명시)
- **컨테이너 삭제**: 컨테이너 상세 탭의 **Delete Container** 버튼으로 강제 삭제 (실행 중 컨테이너 포함)
- **멀티노드 지원**: nodeId 파라미터로 원격 노드 컨테이너도 업데이트/삭제 가능
- **REST API**: `POST /api/containers/{id}/update-image`, `DELETE /api/containers/{id}`

### SSH 키 패스프레이즈 지원
- **암호화 키 사용**: 패스프레이즈로 암호화된 SSH 키로 원격 노드 연결 가능
- **UI 입력**: /nodes 페이지 노드 추가 폼에서 패스프레이즈 직접 입력
- **호스트 키 공유**: `~/.ssh:/root/.ssh:ro` 볼륨 마운트로 로컬 SSH 키 재사용

### 기타
- **재연결 백오프**: 연결 끊김 시 지수 백오프로 자동 재연결
- **컨테이너 필터링**: 정규식 패턴으로 모니터링 대상 필터링
- **설정 검증**: 시작 시 필수 설정 자동 검증

## 기술 스택

| 구성 요소 | 기술 |
|----------|------|
| Framework | Spring Boot 3.2.3 |
| Java | 17+ |
| Docker Client | docker-java 3.3.4 |
| Build Tool | Maven |
| Email | Spring Mail (Jakarta Mail) |
| Template | Thymeleaf |
| WebSocket | Spring WebSocket |
| Security | Spring Security + JJWT 0.12.5 |

## 빌드 및 실행

### 요구 사항

- Java 17 이상
- Maven 3.6 이상
- Docker 실행 환경

### 빌드

```bash
# 테스트 실행
mvn test

# 패키지 빌드
mvn clean package -DskipTests
```

### 실행

#### 기본 실행

```bash
java -jar target/docker-monitor-1.0.0.jar
```

#### Gmail SMTP 사용

```bash
java -jar target/docker-monitor-1.0.0.jar \
  --spring.mail.host=smtp.gmail.com \
  --spring.mail.port=587 \
  --spring.mail.username=your-email@gmail.com \
  --spring.mail.password=your-app-password \
  --docker.monitor.notification.email.to=admin@example.com \
  --docker.monitor.server-name=Production-Server
```

#### 환경 변수 사용

```bash
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
export ALERT_EMAIL=admin@example.com
export SERVER_NAME=Production-Server-01

java -jar target/docker-monitor-1.0.0.jar
```

### Docker로 실행

```bash
# 이미지 빌드
docker build -t docker-monitor .

# 실행
docker run -d \
  --name docker-monitor \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 8080:8080 \
  -e MAIL_USERNAME=your-email@gmail.com \
  -e MAIL_PASSWORD=your-app-password \
  -e ALERT_EMAIL=admin@example.com \
  -e SERVER_NAME=Production-Server \
  docker-monitor
```

## 설정

### application.yml

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
    log-tail-lines: ${LOG_TAIL_LINES:50}
    server-name: ${SERVER_NAME:Unknown Server}
    notification:
      email:
        to: ${ALERT_EMAIL}
        from: ${MAIL_FROM:docker-monitor@example.com}
```

### 자가치유 설정

```yaml
docker:
  monitor:
    self-healing:
      enabled: true
      reset-window-minutes: 30  # 재시작 횟수 리셋 시간 (분)
      rules:
        - name-pattern: "web-*"
          max-restarts: 3
          restart-delay-seconds: 10
        - name-pattern: "worker-*"
          max-restarts: 5
          restart-delay-seconds: 5
        - name-pattern: "db-*"
          max-restarts: 1
          restart-delay-seconds: 30
```

### 라벨 기반 자가치유 설정

docker-compose.yml에서 컨테이너별로 자가치유를 설정할 수 있습니다. 라벨 설정은 yml 규칙보다 우선합니다.

```yaml
services:
  web:
    image: nginx
    labels:
      self-healing.enabled: "true"
      self-healing.max-restarts: "3"
      self-healing.restart-delay-seconds: "10"

  db:
    image: postgres
    labels:
      self-healing.enabled: "false"  # 자가치유 비활성화
```

### 주요 설정 옵션

| 환경 변수 | 설명 | 기본값 |
|----------|------|--------|
| `DOCKER_HOST` | Docker 소켓 경로 | `unix:///var/run/docker.sock` |
| `MAIL_USERNAME` | SMTP 사용자명 | - |
| `MAIL_PASSWORD` | SMTP 비밀번호 | - |
| `ALERT_EMAIL` | 알림 수신 이메일 (쉼표로 구분) | - |
| `MAIL_FROM` | 발신자 이메일 | `docker-monitor@example.com` |
| `SERVER_NAME` | 서버 식별 이름 | `Production-Server-01` |
| `LOG_TAIL_LINES` | 수집할 로그 줄 수 | `50` |
| `RECONNECT_MAX_RETRIES` | 최대 재연결 시도 횟수 | `10` |
| `DEDUP_ENABLED` | 알림 중복 방지 활성화 | `true` |
| `DEDUP_WINDOW_SECONDS` | 중복 판정 시간 창 (초) | `60` |
| `METRICS_ENABLED` | 메트릭 수집 활성화 | `true` |
| `METRICS_COLLECTION_INTERVAL` | 메트릭 수집 주기 (초) | `15` |
| `CPU_THRESHOLD_PERCENT` | CPU 임계치 알림 (%) | `80` |
| `MEMORY_THRESHOLD_PERCENT` | 메모리 임계치 알림 (%) | `90` |
| `RESTART_LOOP_THRESHOLD` | 재시작 반복 알림 횟수 | `3` |
| `RESTART_LOOP_WINDOW` | 재시작 반복 판정 시간 (분) | `5` |
| `SECURITY_ENABLED` | JWT 인증 활성화 | `false` |
| `JWT_SECRET` | JWT 서명 비밀키 | (기본 제공) |
| `JWT_EXPIRATION` | JWT 만료 시간 (초) | `86400` |
| `AUTH_USERNAME` | 인증 사용자명 | `admin` |
| `AUTH_PASSWORD` | 인증 비밀번호 | `admin` |

### 컨테이너 필터링 설정

`application.yml`에서 정규식 패턴으로 모니터링 대상을 필터링할 수 있습니다:

```yaml
docker:
  monitor:
    filter:
      # 모니터링 제외 (정규식)
      exclude-names:
        - ".*-temp"
        - "test-.*"
      exclude-images:
        - "busybox.*"
      # 모니터링 포함 (비어있으면 모두 포함)
      include-names: []
      include-images: []
```

### 재연결 설정

Docker 연결이 끊어졌을 때 지수 백오프로 자동 재연결합니다:

```yaml
docker:
  monitor:
    reconnect:
      initial-delay-ms: 5000    # 초기 대기 시간
      max-delay-ms: 300000      # 최대 대기 시간 (5분)
      multiplier: 2.0           # 백오프 배수
      max-retries: 10           # 최대 재시도 (0=무제한)
```

## 대시보드

웹 브라우저에서 `http://localhost:8080`으로 접속하면 대시보드를 확인할 수 있습니다.

### 주요 페이지

| 경로 | 설명 |
|------|------|
| `/` | 메인 대시보드 - 컨테이너 목록 및 상태 |
| `/container/{id}` | 컨테이너 상세 정보 |
| `/healing-logs` | 자가치유 이력 조회 |
| `/approvals` | 승인 대기 목록 및 처리 |
| `/incidents` | 장애 리포트 목록 |
| `/incidents/{id}` | 장애 리포트 상세 |
| `/suggestions` | AI 패턴 제안 목록 |
| `/metrics-history` | 메트릭 히스토리 & 집계 대시보드 |
| `/ai-settings` | AI 프로바이더 및 API 키 설정 |
| `/nodes` | 노드 목록 및 SSH/TCP 등록 |
| `/login` | 로그인 페이지 (인증 활성화 시) |

### JWT 인증 설정

JWT 인증은 기본적으로 비활성화되어 있습니다. 활성화하려면:

```yaml
docker:
  monitor:
    security:
      enabled: true
      jwt:
        secret: ${JWT_SECRET:your-secret-key-here}
        expiration-seconds: 86400  # 24시간
      user:
        username: ${AUTH_USERNAME:admin}
        password: ${AUTH_PASSWORD:admin}
```

또는 환경 변수로 설정:

```bash
export SECURITY_ENABLED=true
export JWT_SECRET=your-secure-secret-key-minimum-32-characters
export AUTH_USERNAME=admin
export AUTH_PASSWORD=your-secure-password
```

## Exit Code 분석

서비스는 다양한 Exit Code를 분석하여 종료 원인을 파악합니다:

| Exit Code | 의미 | 가능한 원인 |
|-----------|------|------------|
| 0 | 정상 종료 | 프로세스가 성공적으로 완료됨 |
| 1 | 일반 에러 | 애플리케이션 내부 에러, 설정 오류 |
| 126 | 실행 불가 | 권한 문제 또는 실행 파일이 아님 |
| 127 | 명령 없음 | PATH에 명령이 없거나 오타 |
| 137 | SIGKILL | OOM Killer, docker kill, 강제 종료 |
| 139 | SIGSEGV | 세그멘테이션 폴트, 메모리 접근 위반 |
| 143 | SIGTERM | docker stop, 정상 종료 요청 |

## 알림 이메일 예시

### 컨테이너 종료 알림

```
[DOWN] 컨테이너 종료 알림: my-app (Production-Server)

서버: Production-Server
컨테이너 이름: my-app
컨테이너 ID: abc123def456
이미지: nginx:latest
종료 시간: 2026-03-10 14:30:22
Exit Code: 137
이벤트 타입: KILL
OOM Killed: NO

종료 원인 분석:
[Exit Code: 137] SIGKILL (9) - 강제 종료됨

마지막 로그:
2026-03-10T14:30:20Z ERROR Connection timeout
2026-03-10T14:30:21Z FATAL Shutting down...
```

### 자가치유 실패 알림

```
[RESTART FAILED] web-server - 자가치유 실패 (Production-Server)

컨테이너 재시작이 실패했습니다. 수동으로 확인이 필요합니다.
```

### 최대 재시작 초과 알림

```
[MAX RESTARTS] web-server - 최대 재시작 횟수 초과 (Production-Server)

이 컨테이너는 최대 재시작 횟수(3회)에 도달하여
더 이상 자동 재시작되지 않습니다.
```

## Gmail 앱 비밀번호 설정

Gmail을 SMTP 서버로 사용하려면 앱 비밀번호가 필요합니다:

1. Google 계정 > 보안 > 2단계 인증 활성화
2. Google 계정 > 보안 > 앱 비밀번호
3. 앱 선택: 메일, 기기 선택: 기타(맞춤 이름)
4. 생성된 16자리 비밀번호를 `MAIL_PASSWORD`로 사용

## 테스트

```bash
# 전체 테스트 실행
mvn test

# 특정 테스트 클래스 실행
mvn test -Dtest=SelfHealingServiceTest

# 테스트 커버리지 리포트
mvn test jacoco:report
```

### 테스트 커버리지

총 **581개** 테스트 케이스

| 영역 | 테스트 항목 | 개수 |
|------|------------|:----:|
| ExitCodeAnalyzer | Exit Code별 분석, OOM 감지, null 처리 | 12 |
| DockerService | 컨테이너 정보 조회, 로그 수집, 재시작 | 8 |
| EmailNotificationService | 이메일 전송, 자가치유 알림, 임계치 알림 | 12 |
| DockerEventListener | 이벤트 감지, 필터링, 재연결 | 11 |
| SelfHealingService | 자가치유 로직, 라벨 우선순위, 알림 | 13 |
| RestartTracker | 재시작 횟수 추적, 시간 윈도우 리셋 | 6 |
| HealingRuleMatcher | 패턴 매칭, 와일드카드 | 4 |
| ContainerLabelReader | 라벨 읽기, 설정 파싱 | 7 |
| HealingEventRepository | 이력 저장, 조회, 필터링 | 7 |
| MetricsCollector | CPU/메모리/네트워크 메트릭 수집 | 4 |
| MetricsScheduler | 주기적 메트릭 수집, 캐시 관리 | 5 |
| MetricsProperties | 메트릭 설정값 테스트 | 3 |
| LogSearchService | 로그 검색, 필터링, 하이라이트, 스레드/traceId 필터 | 16 |
| ThresholdAlertService | CPU/메모리 임계치 알림 | 6 |
| RestartLoopAlertService | 재시작 반복 감지, 알림 | 4 |
| Playbook | 스키마, 파서, 레지스트리, 실행기, 핸들러 | 24 |
| Safety Gate | 위험도 산정, 서비스 중요도, 시간대별 가중치, 자동 차단 | 20 |
| Human-in-the-Loop | 승인 대기, 승인/거부, 타임아웃 | 27 |
| Audit Logger | 감사 로그, 통계, 보존 정책 | 31 |
| AI 멀티 프로바이더 | Anthropic/OpenAI/Gemini 클라이언트, 라우터, 설정 | 23 |
| 로그 수집 및 분석 | 로그 스트리밍, 보존, AI 로그 분석 | 21 |
| 장애 리포트 | 엔티티, 리포지토리, 서비스, 컨트롤러 | 10 |
| 패턴 감지 및 제안 | 패턴 감지, 설정/Playbook 제안, 승인/거부 | 14 |
| 메트릭 히스토리 | 히스토리 저장, 자가치유 통계, 타임라인, CSV | 15 |
| 차트 대시보드 | 멀티 컨테이너 비교, 게이지 패널, 자동 갱신, 커스텀 날짜 피커 | 12 |
| 메트릭 집계 | 시간대별 평균/최대 집계, 컨테이너 통계 API, 집계 바차트 | 7 |
| 메트릭 보존 | 30일 보존 정책, 만료 삭제 스케줄러, 일별 평균 압축 | 6 |
| GHCR 이미지 자동 업데이트 | GhcrClient, ImageUpdatePoller, ContainerRecreator, RollingUpdateService, ImageWatchEntity/Service/Controller, ImageUpdateHistoryEntity/Service | 48 |
| Health Check Probe | ProbeRunner (HTTP/TCP/EXEC), HealthCheckScheduler | 11 |
| Desired State 관리 | CrashLoopBackoffTracker, StateReconciler, DesiredServiceSpecEntity, DesiredStateService, DesiredStateController | 37 |
| 자가치유 규칙 DB 관리 (Phase 7.12) | SelfHealingRuleEntity, SelfHealingRuleRepository, SelfHealingRuleService, SelfHealingRuleController, HealingRuleMatcher YAML+DB 병합 | 16 |
| 의도적 종료 분류 (Phase 7.14) | OwnActionTracker (TTL), ContainerDeathEvent.intentional, IntentionalDeathClassifier, DockerEventListener 게이트 | 22 |
| 이메일 구독 알림 라우팅 (Phase 7.17) | EmailSubscriptionEntity/Repository/Service(findRecipientsFor)/Controller, EmailNotificationService per-recipient 전송 | 37 |
| 다중 노드 스케줄링 | NodeRegistry, PlacementStrategy, NodeHeartbeatChecker, ContainerMigrator, SSH tunnel, 원격 메트릭 | 17 |
| 배포 전략 | RollingUpdate, Recreate, BlueGreen, Canary | 12 |
| Service Definitions | ComposeParser profiles 파싱/필터링, ServiceDeployer, ServiceDefinition 엔티티 | 20 |
| 멀티 노드 통합 모니터링 (Phase 6) | 원격 컨테이너 통합, SSH 터널, 원격 메트릭 수집, 노드 리소스, 런타임 등록, TCP/SSH 배지 | 30 |
| SSH_PROXY / CP 세션 공유 (Phase 7.1-C) | isSshProxy, requiresTunnel, openProxyTunnel 예외, CP 세션 공유·재연결·선택적 해제 | 13 |
| 이미지 업데이트 & 컨테이너 관리 | ContainerRecreateService (이미지 추출, pull, config 빌드, 재생성 순서, 멀티노드), ContainerController (업데이트, 삭제), ImageService (삭제, exited 컨테이너 정리) | 11 |
| 노드별 컨테이너 필터 | DashboardController 노드 필터링, 노드 목록 전달, 프론트엔드 드롭다운 | 4 |
| 기타 | 필터링, 중복방지, 설정검증 등 | 22 |

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Docker Monitor Service                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────┐    ┌──────────────────┐                        │
│  │ Web Dashboard   │    │ ConfigValidator  │                        │
│  │ (Thymeleaf)     │    │ (시작 시 검증)    │                        │
│  └─────────────────┘    └──────────────────┘                        │
│                                                                      │
│  ┌─────────────────┐    ┌──────────────────┐    ┌────────────────┐  │
│  │ DockerEvent     │───▶│ Deduplication    │───▶│ Container      │  │
│  │ Listener        │    │ Service          │    │ Filter         │  │
│  │ (이벤트 감지)    │    │ (중복 방지)       │    │ (필터링)        │  │
│  └─────────────────┘    └──────────────────┘    └───────┬────────┘  │
│                                                          │           │
│           ┌──────────────────────────────────────────────┤           │
│           │                                              │           │
│           ▼                                              ▼           │
│  ┌─────────────────┐    ┌──────────────────┐    ┌────────────────┐  │
│  │ SelfHealing     │    │ ExitCode         │    │ Email          │  │
│  │ Service         │    │ Analyzer         │    │ Notification   │  │
│  │ (자가치유)       │    │ (원인 분석)       │    │ Service        │  │
│  └────────┬────────┘    └──────────────────┘    └────────────────┘  │
│           │                                              ▲           │
│           │         ┌──────────────────┐                 │           │
│           ├────────▶│ RestartTracker   │                 │           │
│           │         │ (횟수 추적)       │                 │           │
│           │         └──────────────────┘                 │           │
│           │                                              │           │
│           │         ┌──────────────────┐                 │           │
│           ├────────▶│ HealingRule      │                 │           │
│           │         │ Matcher          │                 │           │
│           │         └──────────────────┘                 │           │
│           │                                              │           │
│           └──────────────────────────────────────────────┘           │
│                              (알림 전송)                              │
│                                                                      │
│  ┌─────────────────┐    ┌──────────────────┐                        │
│  │ HealingEvent    │    │ DockerService    │                        │
│  │ Repository      │    │ (Docker API)     │                        │
│  │ (이력 저장)      │    │                  │                        │
│  └─────────────────┘    └──────────────────┘                        │
│                                                                      │
└──────────────────────────────────┬──────────────────────────────────┘
                                   │
                                   ▼
                      ┌────────────────────────┐
                      │   Docker Engine API    │
                      │  (Unix Socket / TCP)   │
                      └────────────────────────┘
```

## 트러블슈팅

### Docker 소켓 연결 실패

```
Connection refused: /var/run/docker.sock
```

**해결 방법:**
- Docker Desktop이 실행 중인지 확인
- 소켓 파일 권한 확인: `ls -la /var/run/docker.sock`
- Mac: Docker Desktop 설정에서 "Allow the default Docker socket" 활성화

### 이메일 전송 실패

```
Mail server connection failed
```

**해결 방법:**
- SMTP 호스트/포트 설정 확인
- Gmail 사용 시 앱 비밀번호 사용 (일반 비밀번호 X)
- 방화벽에서 SMTP 포트(587, 465) 허용

### OOM 이벤트가 감지되지 않음

Docker 컨테이너의 메모리 제한이 설정되어 있어야 OOM 이벤트가 발생합니다:

```bash
docker run -m 100m --memory-swap 100m your-image
```

### 자가치유가 동작하지 않음

1. `self-healing.enabled: true` 설정 확인
2. 컨테이너 이름이 규칙 패턴과 일치하는지 확인
3. 최대 재시작 횟수에 도달하지 않았는지 확인
4. 라벨에 `self-healing.enabled: "false"`가 설정되어 있는지 확인

