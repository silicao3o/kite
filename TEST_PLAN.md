# Docker Monitor Phase 1~5 테스트 계획서

## 테스트 환경

| 항목 | 값 |
|------|----|
| Docker Monitor | http://localhost:8080 |
| exercise-auth | http://localhost:8081 (ghcr.io/silicao3o/exercise-auth) |
| schedule-diary | http://localhost:8082 (ghcr.io/silicao3o/schedule-diary) |
| Mailhog (이메일) | http://localhost:8025 |
| GitHub 유저 | silicao3o |
| AI 제공자 | Gemini (기본) |

---

## 사전 준비 (최초 1회)

### Step 1 — 이미지 빌드 & GHCR 푸시

```bash
cd /Users/silica/docker-monitor
bash scripts/ghcr-setup.sh
```

> 소요 시간: exercise-auth(Maven) ~5분, schedule-diary(Gradle) ~5분

### Step 2 — 스택 실행

```bash
cd /Users/silica/docker-monitor
docker compose -f docker-compose.yml --env-file .env up --build -d
```

### Step 3 — 기동 확인 (1~2분 소요)

```bash
bash scripts/test-scenarios.sh status
```

---

## Phase 1: 기본 모니터링 + 알림

### 1-1. 컨테이너 목록 & 메트릭 확인

**방법**: http://localhost:8080 접속

**확인 항목**:
- [ ] exercise-auth, schedule-diary, mailhog, docker-monitor 목록 표시
- [ ] CPU%, 메모리% 수치가 10초마다 갱신 (WebSocket 실시간)
- [ ] 각 컨테이너의 상태 badge (running/exited)

---

### 1-2. 컨테이너 상세 & 로그 조회

**방법**: 컨테이너 이름 클릭 → 상세 페이지

**확인 항목**:
- [ ] 컨테이너 기본 정보 (이미지, 생성 시각, 포트)
- [ ] 최근 로그 50줄 표시
- [ ] 로그 키워드 검색 (예: `ERROR`)
- [ ] 로그 레벨 필터 (ERROR / WARN / INFO)

---

### 1-3. 이메일 알림 — 컨테이너 종료

```bash
bash scripts/test-scenarios.sh 1-alert
```

**확인 항목**:
- [ ] Docker Monitor 이벤트 감지 (5초 이내)
- [ ] http://localhost:8025 에서 `[DOWN] exercise-auth` 이메일 수신
- [ ] 이메일 내 Exit Code, 마지막 로그, 종료 원인 포함

---

### 1-4. 이메일 알림 — CPU 임계치 초과

```bash
bash scripts/test-scenarios.sh 2-cpu
```

**설정**: CPU_THRESHOLD_PERCENT=60

**확인 항목**:
- [ ] http://localhost:8025 에서 `[CPU HIGH]` 이메일 수신
- [ ] 알림 중복 방지: 30초 내 동일 컨테이너 중복 알림 없음

---

### 1-5. 로그 검색 페이지

**방법**: 컨테이너 상세 → Log Search 탭

**확인 항목**:
- [ ] 키워드 검색 결과 하이라이트
- [ ] 시간 범위 필터 (from/to)
- [ ] 로그 레벨 필터 체크박스

---

## Phase 2: 규칙 기반 자가치유

### 2-1. 자가치유 — 컨테이너 충돌 후 자동 재시작

```bash
bash scripts/test-scenarios.sh 2-selfheal
```

**설정**: self-healing.max-restarts=5, restart-delay-seconds=5

**확인 항목**:
- [ ] 충돌 후 5초 이내 재시작 시작
- [ ] http://localhost:8080/healing-logs 에 재시작 이력 기록
- [ ] 재시작 성공 여부 (success=true)

---

### 2-2. 라벨 기반 자가치유 설정 확인

**방법**: http://localhost:8080 → 컨테이너 목록 → 자가치유 열 확인

**확인 항목**:
- [ ] exercise-auth: 자가치유 ON, 최대 5회
- [ ] schedule-diary: 자가치유 ON, 최대 5회
- [ ] mailhog: 자가치유 OFF (라벨 설정)
- [ ] docker-monitor: 자가치유 OFF (라벨 설정)

---

### 2-3. 최대 재시작 횟수 초과

```bash
bash scripts/test-scenarios.sh 2-maxrestart
```

**확인 항목**:
- [ ] http://localhost:8025 에서 `[MAX RESTARTS]` 이메일 수신
- [ ] 6번째 충돌 시 더 이상 재시작 안 함
- [ ] 자가치유 로그에 최대 횟수 초과 기록

---

### 2-4. Playbook 시스템

**방법**: http://localhost:8080 → Playbook 또는 Approvals 메뉴

**확인 항목**:
- [ ] 등록된 Playbook 목록 확인 (container-restart, oom-recovery 등)
- [ ] Playbook 실행 이력 확인

---

### 2-5. Safety Gate & Human-in-the-Loop

**방법**: HIGH/CRITICAL 위험도 작업 실행 시도

**확인 항목**:
- [ ] http://localhost:8080/approvals 페이지 접속
- [ ] 승인 대기 목록 확인
- [ ] 승인/거부 버튼 동작
- [ ] 5분 후 자동 만료 확인

---

### 2-6. Audit Log

**방법**: http://localhost:8080 → Audit Log 메뉴

**확인 항목**:
- [ ] 모든 조치에 대한 감사 로그 기록
- [ ] 의도(intent), 실행결과(result), reasoning 포함
- [ ] Append-Only 확인 (수정/삭제 불가)

---

## Phase 3: AI 사후 분석

### 3-1. AI 분석 트리거 — 충돌 후 자동 분석

```bash
bash scripts/test-scenarios.sh 3-ai
```

**확인 항목**:
- [ ] http://localhost:8080 → Incident Reports 에 리포트 생성
- [ ] Root Cause 분석 결과 포함
- [ ] 재발 방지 제안 (Suggestions) 생성

---

### 3-2. 패턴 감지 & 제안

**방법**: 동일 컨테이너 3회 이상 반복 충돌 후 Suggestions 확인

**확인 항목**:
- [ ] http://localhost:8080 → Suggestions 에 반복 패턴 감지 기록
- [ ] 설정 최적화 제안 (메모리 한도, 재시작 정책 등)
- [ ] 제안 승인/거부 UI 동작

---

### 3-3. 메트릭 히스토리 차트

**방법**: http://localhost:8080 → Metrics History

**확인 항목**:
- [ ] 시간별 CPU/메모리 추이 차트 (Chart.js)
- [ ] 컨테이너 선택 필터
- [ ] CSV 내보내기 버튼
- [ ] 자가치유 통계 (MTTR, 성공률)

---

## Phase 4: 대시보드 고도화

### 4-1. 멀티 컨테이너 비교 차트

**방법**: http://localhost:8080 → Metrics History → 멀티 선택

**확인 항목**:
- [ ] exercise-auth + schedule-diary 동시 표시 (멀티 라인)
- [ ] 현재 상태 게이지 패널
- [ ] 30초 자동 갱신

---

### 4-2. 시간 범위 & 집계

**방법**: Metrics History → 날짜 피커 → 집계 API

**확인 항목**:
- [ ] 커스텀 시간 범위 설정
- [ ] 시간대별 평균/최대 집계 표시

---

## Phase 5: K8s-like 오케스트레이션

### 5-1. GHCR 이미지 자동 업데이트

**목표**: 새 이미지 푸시 → 60초 내 감지 → 자동 Rolling Update

```bash
# 소스 변경 없이 동일 이미지를 다시 푸시 (digest 가 바뀜)
bash scripts/ghcr-setup.sh
```

**또는 소스 파일 1줄 수정 후 재빌드**:
```bash
# exercise-auth/src/main/resources/application.yml 에 주석 한 줄 추가
echo "# test-$(date)" >> /Users/silica/IdeaProjects/exercise-auth/src/main/resources/application.yml
bash scripts/ghcr-setup.sh
```

**확인 항목**:
- [ ] Docker Monitor 로그에 새 digest 감지 메시지
- [ ] exercise-auth 컨테이너 자동 재시작 (Rolling Update)
- [ ] 업데이트 중 잠시 중단 후 새 버전으로 복구
- [ ] 업데이트 실패 시 자동 롤백 동작

```bash
docker logs docker-monitor 2>&1 | grep -i "digest\|update\|rolling" | tail -20
```

---

### 5-2. Health Check Probe

**목표**: HTTP Liveness Probe 실패 → 재시작 트리거

```bash
bash scripts/test-scenarios.sh 5-2-healthcheck
```

> exercise-auth 내부 프로세스를 일시정지(SIGSTOP) → /actuator/health 응답 불가
> → 3회 연속 실패(failure-threshold=3, 15초 간격) → 자동 재시작

**타임라인**:
- T+0s: 프로세스 정지
- T+15s: 1차 probe 실패
- T+30s: 2차 probe 실패
- T+45s: 3차 probe 실패 → 재시작 트리거
- T+50s: 컨테이너 재시작 완료

**확인 항목**:
- [ ] `docker logs docker-monitor | grep -i liveness` — 실패 감지 로그
- [ ] 자가치유 로그와 별개로 HealthCheck 트리거 재시작 기록
- [ ] http://localhost:8080/healing-logs — HealthCheck 이력

---

### 5-3. Desired State 관리

**목표**: 컨테이너 삭제 → 30초 내 자동 재생성

```bash
bash scripts/test-scenarios.sh 5-3-desiredstate
```

**확인 항목**:
- [ ] `docker ps` 로 schedule-diary 가 30초 내 자동 재생성
- [ ] 재생성된 컨테이너가 원래 환경변수/포트 설정 그대로
- [ ] `docker logs docker-monitor | grep -i "reconcil\|desired"` — 재생성 로그

---

### 5-4. 다중 노드 스케줄링

> **단일 호스트 환경에서는 검증 불가**
> 다중 Docker 호스트(TCP 2375 포트 개방)가 필요합니다.

**설정 방법** (다중 호스트 환경):
```yaml
# docker/test-config/application.yml
docker.monitor.nodes:
  enabled: true
  nodes:
    - name: host-1
      host: 192.168.1.10
      port: 2375
    - name: host-2
      host: 192.168.1.11
      port: 2375
```

**확인 항목** (환경 구성 시):
- [ ] 노드 목록 및 리소스 사용량 표시
- [ ] 컨테이너 배치 전략 (LEAST_USED) 동작
- [ ] 노드 장애(Heartbeat 중단) → 다른 노드로 컨테이너 이동

---

### 5-5. 배포 전략

**목표**: Rolling Update, Blue-Green, Canary 전략 확인

```bash
bash scripts/test-scenarios.sh 5-5-deploy
```

**Rolling Update 확인**:
- [ ] 컨테이너 순차 재시작 (무중단)
- [ ] max-unavailable 설정 준수

**Blue-Green 확인** (API 직접 호출):
```bash
curl -X POST http://localhost:8080/api/deploy/blue-green \
  -H 'Content-Type: application/json' \
  -d '{"container":"exercise-auth","image":"ghcr.io/silicao3o/exercise-auth:latest"}'
```

**Canary 확인**:
```bash
curl -X POST http://localhost:8080/api/deploy/canary \
  -H 'Content-Type: application/json' \
  -d '{"container":"schedule-diary","image":"ghcr.io/silicao3o/schedule-diary:latest","percentage":50}'
```

---

## 트러블슈팅

### 컨테이너가 시작되지 않는 경우
```bash
docker logs exercise-auth
docker logs schedule-diary
```

### GHCR 이미지 pull 실패
```bash
# GHCR 로그인 후 재시도
echo "$GHCR_TOKEN" | docker login ghcr.io -u silicao3o --password-stdin
docker compose -f docker-compose.test.yml --env-file .env pull
```

### exercise-auth DB 연결 오류
- Neon DB 가 idle 상태일 수 있음 → 첫 요청 시 10~15초 소요
- `curl http://localhost:8081/actuator/health` 로 확인

### schedule-diary 8082 포트 충돌
```bash
lsof -i :8082   # 포트 점유 프로세스 확인
```

### 전체 초기화
```bash
docker compose -f docker-compose.test.yml --env-file .env down -v
docker compose -f docker-compose.test.yml --env-file .env up --build -d
```

---

## 빠른 명령어 참조

```bash
# 스택 시작
docker compose -f docker-compose.test.yml --env-file .env up --build -d

# 스택 상태
bash scripts/test-scenarios.sh status

# 로그 실시간 확인
docker logs -f docker-monitor

# 컨테이너 강제 종료 (자가치유 테스트)
docker kill --signal=SIGKILL exercise-auth

# 컨테이너 삭제 (Desired State 테스트)
docker stop schedule-diary && docker rm schedule-diary

# 이미지 재빌드 & 푸시 (GHCR 자동 업데이트 테스트)
bash scripts/ghcr-setup.sh

# 전체 정리
docker compose -f docker-compose.test.yml --env-file .env down
```
