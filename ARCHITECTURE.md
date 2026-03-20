# docker-monitor 패키지별 아키텍처 상세 설명

> **총 23개 패키지, 170+ 클래스**로 구성된 Kubernetes 스타일 컨테이너 오케스트레이션 시스템

---

## 아키텍처 레이어 구조

```
UI/API 레이어        → controller, websocket
서비스 레이어         → service, metrics, health, log, update
오케스트레이션 레이어  → playbook, approval, audit, incident
인프라 레이어         → node, deploy, desired, ai
기반 레이어           → model, config, security, util, analyzer
```

## 이벤트 흐름 요약

```
Docker 데몬
    ↓
DockerEventListener          ← 이벤트 진입점
    ↓
DockerService / SelfHealingService
    ↓
PlaybookExecutor → SafetyGate
    ↓
ApprovalService (고위험 시)  ← 인간 승인 게이트
    ↓
AuditLogService              ← 모든 액션 감사 기록
    ↓
IncidentReportService → SuggestionService
```

---

## 패키지별 상세 설명

### 1. `com.lite_k8s` (루트)

- **DockerMonitorApplication** — Spring Boot 진입점, 스케줄링 활성화 및 데몬 모드 지원

---

### 2. `com.lite_k8s.ai` (AI 연동)

다중 AI 프로바이더를 통한 이상 탐지 및 로그 분석

| 클래스 | 역할 |
|--------|------|
| `AiClient` (interface) | AI 클라이언트 공통 계약 |
| `AiClientSelector` | 활성화된 프로바이더로 라우팅 |
| `AiSettingsService/Controller` | AI 설정 관리 (REST) |
| `AnthropicAiClient` | Claude(Anthropic) 연동 |
| `OpenAiClient` | OpenAI GPT 연동 |
| `GeminiAiClient` | Google Gemini 연동 |
| `ClaudeCodeClient` | Claude Code 전용 연동 |
| `AiResponseParser` | AI 응답 파싱 → 표준화 포맷 변환 |
| `AiResponseConverter` | 컨테이너 메트릭 → AI 컨텍스트 변환 |
| `LogAnalysisService` | AI 기반 로그 분석 |
| `AiFreeJudgmentService` | 외부 호출 없는 경량 분석 |
| `AnomalyContextBuilder` | 분석 컨텍스트 빌더 패턴 |
| `AiJudgmentResult` | AI 결정 결과 및 근거 모델 |
| `AiProvider` (enum) | ANTHROPIC, OPENAI, GEMINI |

---

### 3. `com.lite_k8s.approval` (승인 워크플로우)

고위험 자동화 작업에 대한 인간 승인 게이트

| 클래스 | 역할 |
|--------|------|
| `ApprovalService` | 요청/승인/거부 워크플로우 |
| `ApprovalController` | REST 엔드포인트 |
| `PendingApproval` | 승인 요청 도메인 모델 |
| `PendingApprovalRepository` | 승인 요청 영속성 |
| `ApprovalResult` | 승인 작업 결과 |
| `ApprovalExpiryScheduler` | 만료된 요청 자동 처리 스케줄러 |
| `ApprovalStatus` (enum) | PENDING, APPROVED, REJECTED, EXPIRED |

---

### 4. `com.lite_k8s.audit` (감사 로그)

모든 자동화 작업 및 AI 결정에 대한 완전한 감사 추적

| 클래스 | 역할 |
|--------|------|
| `AuditLogService` | 작업 전 주기 전체 로깅 서비스 |
| `AuditLog` | 감사 항목 도메인 모델 |
| `AuditLogRepository` | 감사 로그 영속성 |
| `AuditLogRetentionScheduler` | 오래된 로그 자동 정리 스케줄러 |
| `AuditStatistics` | 성공률/건수 집계 |
| `ExecutionResult` (enum) | SUCCESS, FAILURE, BLOCKED, TIMEOUT, PENDING |

---

### 5. `com.lite_k8s.config` (설정)

Spring 설정 및 애플리케이션 프로퍼티 관리

| 클래스 | 역할 |
|--------|------|
| `DockerConfig` | Docker API 클라이언트 빈 설정 (소켓/HTTP) |
| `DockerClientConfig` | Docker 연결 파라미터 |
| `SecurityConfig` | Spring Security / 인증 설정 |
| `SecurityProperties` | 보안 관련 설정 프로퍼티 |
| `AsyncConfig` | 스레드 풀 및 비동기 작업 설정 |
| `WebSocketConfig` | WebSocket 메시지 핸들링 설정 |
| `MonitorProperties` | 핵심 모니터링 설정 (`@ConfigurationProperties`) |
| `HealthCheckProperties` | 헬스 프로브 설정 |
| `SelfHealingProperties` | 자가 치유 동작 설정 |
| `ConfigurationValidator` | 시작 시 설정 유효성 검사 |

---

### 6. `com.lite_k8s.controller` (웹 컨트롤러)

UI 및 API 클라이언트를 위한 REST/Web 엔드포인트

| 클래스 | 역할 |
|--------|------|
| `DashboardController` | 대시보드 UI 및 컨테이너 상태 엔드포인트 |
| `AuthController` | 인증 및 JWT 토큰 관리 |

---

### 7. `com.lite_k8s.deploy` (배포 전략)

컨테이너 업데이트를 위한 다양한 배포 전략 구현

| 클래스 | 역할 |
|--------|------|
| `DeploymentStrategy` (interface) | 배포 전략 공통 계약 |
| `DeploymentStrategyFactory` | 전략 타입별 인스턴스 생성 팩토리 |
| `RollingUpdateDeployment` | 무중단 점진적 컨테이너 교체 |
| `BlueGreenDeployment` | 두 환경 간 스위칭 |
| `CanaryDeployment` | 일부 컨테이너에만 점진적 배포 |
| `RecreateDeployment` | 정지 후 재시작 방식 |
| `ContainerOperator` | 컨테이너 생명주기 저수준 작업 |
| `DeploymentSpec` | 배포 설정/파라미터 |
| `DeployResult` | 배포 작업 결과 |
| `DeploymentType` (enum) | ROLLING_UPDATE, BLUE_GREEN, CANARY, RECREATE |

---

### 8. `com.lite_k8s.desired` (선언적 상태 관리)

Kubernetes 스타일 선언적 상태 — 실제 상태를 원하는 상태로 지속적으로 조정

| 클래스 | 역할 |
|--------|------|
| `StateReconciler` | 실제 vs 원하는 상태 주기적 조정 |
| `DesiredStateProperties` | 원하는 서비스/레플리카 수 설정 |
| `ContainerFactory` | 스펙 기반 컨테이너 생성 |
| `CrashLoopBackoffTracker` | 크래시 루프 컨테이너 추적 |

---

### 9. `com.lite_k8s.health` (헬스 체크)

Kubernetes 스타일 프로브 (Liveness, Readiness, Startup) 구현

| 클래스 | 역할 |
|--------|------|
| `HealthCheckScheduler` | 전체 컨테이너 대상 주기적 프로브 실행 |
| `ProbeRunner` | 개별 프로브 실행 (TCP/HTTP/Exec) |
| `ProbeConfig` | 프로브 설정 (엔드포인트, 포트, 타임아웃, 임계치) |
| `HealthCheckStateTracker` | 연속 실패 횟수 및 컨테이너 상태 추적 |
| `ProbeResult` | 프로브 실행 결과 (타이밍 포함) |
| `ProbeType` (enum) | TCP, HTTP, EXEC |

---

### 10. `com.lite_k8s.incident` (인시던트 탐지 및 보고)

이상 탐지, 인시던트 리포트 생성, 복구 제안

| 클래스 | 역할 |
|--------|------|
| `IncidentReportService` | 인시던트 생성 및 관리 |
| `IncidentReport` | 근본 원인 분석 포함 도메인 모델 |
| `IncidentReportRepository` | 인시던트 리포트 영속성 |
| `IncidentReportController` | 인시던트 조회 REST 엔드포인트 |
| `IncidentPatternDetector` | 반복 장애 패턴 식별 |
| `IncidentPattern` | 장애 패턴 모델 (OOM, segfault 등) |
| `SuggestionService` | 복구 제안 생성 |
| `Suggestion` | 복구 액션 권고 |
| `SuggestionController` | 복구 제안 REST 엔드포인트 |
| `TimelineEntry` | 인시던트 타임라인 이벤트 |

---

### 11. `com.lite_k8s.listener` (이벤트 리스너)

Docker 데몬 이벤트 수신 및 모니터링 워크플로우 트리거 — **이벤트 흐름의 시작점**

| 클래스 | 역할 |
|--------|------|
| `DockerEventListener` | Docker 이벤트 비동기 수신 (create / start / die / destroy) |

---

### 12. `com.lite_k8s.log` (로그 관리)

보존 정책 및 검색 기능이 있는 컨테이너 로그 저장소

| 클래스 | 역할 |
|--------|------|
| `LogStorageService` | 보존 정책 포함 인메모리 로그 저장 |
| `StoredLog` | 타임스탬프 포함 로그 항목 모델 |
| `LogStorageProperties` | 보존 기간 및 최대 저장량 설정 |
| `LogStorageStats` | 저장 통계 |
| `LogCleanupScheduler` | 오래된 로그 자동 삭제 스케줄러 |

---

### 13. `com.lite_k8s.metrics` (메트릭 수집 및 집계)

컨테이너 성능 메트릭 수집, 저장, 집계, 분석의 전 과정 담당

| 클래스 | 역할 |
|--------|------|
| `MetricsCollector` | CPU/메모리/네트워크 주기적 수집 |
| `MetricsScheduler` | 메트릭 수집 스케줄 관리 |
| `CurrentMetricsGaugeService` | 현재/실시간 메트릭 제공 |
| `ContainerStats` | CPU/메모리/네트워크 메트릭 데이터 |
| `MetricsSnapshot` | 타임스탬프 포함 메트릭 레코드 |
| `MetricsHistoryService` | 이력 메트릭 저장 및 조회 |
| `MetricsHistoryRepository` | 메트릭 이력 영속성 |
| `MetricsHistoryController` | 메트릭 이력 REST 엔드포인트 |
| `MetricsAggregationService` | 시간별/일별 집계 및 통계 |
| `MetricsAggregationController` | 집계 데이터 REST 엔드포인트 |
| `HourlyAggregate` | 시간 단위 집계 메트릭 |
| `MetricsRetentionScheduler` | 오래된 메트릭 자동 정리 스케줄러 |
| `MetricsRetentionProperties` | 보존 정책 설정 |
| `MetricsCsvExporter` | 메트릭 CSV 내보내기 |
| `MetricsDailyCompressor` | 일별 메트릭 압축 요약 |
| `MultiContainerMetricsService` | 전체 컨테이너 집계 |
| `IncidentTimelineService` | 메트릭과 인시던트 이벤트 연결 |
| `GaugeController` | 게이지 디스플레이 REST 엔드포인트 |
| `HealingStatisticsService` | 자가 치유 성공률 계산 |

---

### 14. `com.lite_k8s.model` (도메인 모델)

시스템 전체에서 사용하는 핵심 데이터 구조 (DTO 역할)

| 클래스 | 역할 |
|--------|------|
| `ContainerInfo` | 컨테이너 메타데이터 (id, 이름, 이미지, 상태, 포트, 레이블, 메트릭) |
| `ContainerMetrics` | CPU/메모리/네트워크 메트릭 스냅샷 |
| `ContainerDeathEvent` | 컨테이너 실패/종료 이벤트 (종료 코드, 원인 포함) |
| `HealingEvent` | 자가 치유 액션 이벤트 (restart, recreate, migrate) |
| `LogEntry` | 개별 로그 라인 항목 |
| `LogSearchResult` | 검색 쿼리 결과 및 매칭 로그 |

---

### 15. `com.lite_k8s.node` (멀티 노드 관리) — Phase 6·7

SSH / SSH_PROXY 터널링을 통한 분산 Docker 노드 관리 및 컨테이너 스케줄링

| 클래스 | 역할 |
|--------|------|
| `Node` | Docker 호스트/노드 상태 및 메트릭 표현 |
| `NodeRegistry` | 전체 관리 노드 중앙 레지스트리 (스레드 안전) |
| `NodeConfig` | 노드 등록 설정 (@PostConstruct) — SSH_PROXY 시 proxy 설정 없으면 스킵 |
| `NodeProperties` | 노드 관리 설정 프로퍼티 (ProxyConfig 포함) |
| `NodeDockerClientFactory` | 원격 노드용 Docker 클라이언트 생성 (TCP/SSH/SSH_PROXY 분기) |
| `SshTunnelManager` | SSH·SSH_PROXY 터널 관리 — CP 세션 1개 공유, 포워딩 룰 per-node |
| `JSchSessionFactory` | JSch 세션 생성 추상화 인터페이스 (테스트 주입용) |
| `NodeHeartbeatChecker` | 노드 주기적 헬스 체크 |
| `NodeFailureEvent` | 노드 비가용 이벤트 |
| `NodeManagementController` | 노드 운영 REST 엔드포인트 |
| `NodeViewController` | 노드 조회 REST 엔드포인트 |
| `ContainerMigrator` | 노드 간 컨테이너 이동 |
| `PlacementStrategy` (interface) | 컨테이너 배치 전략 공통 계약 |
| `LeastUsedStrategy` | 가장 덜 사용된 노드에 배치 |
| `RoundRobinStrategy` | 균등 분배 배치 |
| `NodeStatus` (enum) | HEALTHY, UNHEALTHY, UNKNOWN |
| `NodeConnectionType` (enum) | TCP, SSH, SSH_PROXY |

#### SSH_PROXY 터널 구조

```
docker-monitor (GCP)
  │
  └── SshTunnelManager
        ├── sharedCpSession (CP SSH 세션 1개 공유)
        │     ├── 포워딩 룰: localhost:20000 → vm-a:2375
        │     └── 포워딩 룰: localhost:20001 → vm-b:2375
        │
        └── activeSessions: {node-vm-a → cpSession, node-vm-b → cpSession}
              ↓
        NodeDockerClientFactory → DockerClient(tcp://localhost:20000)
                                  DockerClient(tcp://localhost:20001)
```

> VM 노드가 추가될 때마다 `setPortForwardingL(localPort, vmHost, vmPort)` 룰만 추가.
> VM 노드 제거 시 `delPortForwardingL(localPort)`로 룰만 삭제, CP 세션 유지.
> 마지막 SSH_PROXY 노드 제거 시에만 CP 세션 `disconnect()`.

---

### 16. `com.lite_k8s.playbook` (자가 치유 플레이북)

이상 탐지 시 자동 복구 액션 정의 및 실행 오케스트레이션

| 클래스 | 역할 |
|--------|------|
| `Playbook` | 이벤트 처리 액션 시퀀스 도메인 모델 |
| `PlaybookDto` | API 통신용 DTO |
| `Trigger` | 트리거 조건 (장애 패턴, 메트릭 임계치) |
| `Action` | 개별 실행 액션 (restart, recreate, scale 등) |
| `ActionHandler` (interface) | 액션 구현체 공통 계약 |
| `ContainerRestartHandler` | 컨테이너 재시작 액션 처리 |
| `DelayHandler` | 딜레이/일시 중지 액션 처리 |
| `ActionResult` | 단일 액션 실행 결과 |
| `PlaybookExecutor` | 순차 액션 실행 오케스트레이션 |
| `PlaybookResult` | 전체 플레이북 실행 결과 |
| `PlaybookRegistry` | 사용 가능한 플레이북 중앙 저장소 |
| `PlaybookLoader` | 설정/파일에서 플레이북 로드 |
| `PlaybookParser` | YAML/JSON 플레이북 정의 파싱 |
| `SafetyGate` | 고위험 액션 실행 전 안전 점검 |
| `SafetyGateProperties` | 안전 게이트 설정 |
| `SafetyGateResult` | 안전 게이트 결정 및 사유 |
| `RiskAssessor` | 액션 위험 수준 평가 |
| `TimeBasedRiskElevator` | 시간대별 위험 수준 조정 |
| `ServiceCriticalityResolver` | 레이블/설정으로 서비스 중요도 판단 |
| `ServiceCriticalityRule` | 중요도 매칭 규칙 |
| `RiskLevel` (enum) | LOW, MEDIUM, HIGH, CRITICAL |
| `ServiceCriticality` (enum) | CRITICAL, HIGH, MEDIUM, LOW |

---

### 17. `com.lite_k8s.repository` (데이터 접근)

특정 엔티티를 위한 데이터 영속성 레이어

| 클래스 | 역할 |
|--------|------|
| `HealingEventRepository` | 자가 치유 이벤트 이력 영속성 |

---

### 18. `com.lite_k8s.security` (보안 / 인증)

JWT 기반 인증 및 권한 부여

| 클래스 | 역할 |
|--------|------|
| `JwtService` | JWT 토큰 생성/검증/클레임 추출 |
| `JwtAuthenticationFilter` | JWT 검증 요청 필터 |

---

### 19. `com.lite_k8s.service` (핵심 비즈니스 로직)

주요 비즈니스 로직 및 서비스 오케스트레이션 — 시스템의 중심 허브

| 클래스 | 역할 |
|--------|------|
| `DockerService` | Docker API 래퍼 (목록, 검사, 로그, 통계) |
| `SelfHealingService` | 플레이북 선택 및 실행 오케스트레이션 |
| `HealingRuleMatcher` | 컨테이너 실패 → 치유 규칙 매칭 |
| `RestartTracker` | 재시작 횟수 및 패턴 추적 |
| `RestartLoopAlertService` | 재시작 루프 탐지 및 알림 |
| `ContainerFilterService` | 레이블/이름 기반 컨테이너 필터링 |
| `ContainerLabelReader` | 설정을 위한 컨테이너 레이블 파싱 |
| `LogSearchService` | 컨테이너 로그 전문(Full-text) 검색 |
| `ThresholdAlertService` | 메트릭 임계치 초과 알림 생성 |
| `AlertDeduplicationService` | 중복 알림 방지 |
| `EmailNotificationService` | 심각 이벤트 이메일 알림 |

---

### 20. `com.lite_k8s.update` (컨테이너 이미지 업데이트)

새 이미지 버전 탐지 및 무중단 롤링 업데이트 수행

| 클래스 | 역할 |
|--------|------|
| `ImageUpdatePoller` | 레지스트리에서 새 이미지 버전 주기적 확인 |
| `ImageUpdateDetectedEvent` | 새 버전 발견 시 발행 이벤트 |
| `GhcrClient` | GitHub Container Registry API 클라이언트 |
| `GhcrClientConfig` | GHCR 설정 |
| `RollingUpdateService` | 배포 전략 사용 롤링 업데이트 실행 |
| `ContainerRecreator` | 새 이미지로 컨테이너 재생성 |
| `ImageWatchProperties` | 이미지 모니터링 설정 |
| `UpdateResult` | 업데이트 작업 결과 |

---

### 21. `com.lite_k8s.util` (유틸리티)

| 클래스 | 역할 |
|--------|------|
| `DockerContainerNames` | 컨테이너 이름 파싱/포맷팅 유틸리티 |

---

### 22. `com.lite_k8s.websocket` (실시간 WebSocket)

WebSocket을 통한 클라이언트 실시간 푸시 업데이트

| 클래스 | 역할 |
|--------|------|
| `ContainerStatusHandler` | 컨테이너 상태 변경 실시간 푸시 |
| `LogStreamHandler` | 컨테이너 로그 실시간 스트리밍 |

---

### 23. `com.lite_k8s.analyzer` (분석)

| 클래스 | 역할 |
|--------|------|
| `ExitCodeAnalyzer` | 종료 코드 → 사람이 읽을 수 있는 실패 원인 매핑 |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| 프레임워크 | Spring Boot, Spring Cloud |
| 컨테이너 | Docker API (docker-java) |
| AI 연동 | Anthropic Claude, OpenAI GPT, Google Gemini |
| 보안 | Spring Security + JWT |
| 실시간 통신 | Spring WebSocket |
| 비동기 처리 | Spring Tasks, @Async, @Scheduled |
| 빌드 | Maven |
