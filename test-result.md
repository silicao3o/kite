# Docker Monitor 테스트 결과

**테스트 일자**: 2026-03-18
**테스트 환경**: 로컬 Docker Compose (darwin / Apple Silicon)
**대상 브랜치**: main

---

## 환경 구성

| 서비스 | 이미지 | 포트 |
|--------|--------|------|
| docker-monitor | 로컬 빌드 | 8080 |
| exercise-auth | ghcr.io/silicao3o/exercise-auth:latest | 8081 |
| schedule-diary | ghcr.io/silicao3o/schedule-diary:latest | 8082 |
| mailhog | mailhog/mailhog:latest | 8025 |

---

## Phase 1: 기본 모니터링

| 항목 | 결과 | 비고 |
|------|------|------|
| 대시보드 접속 | ✅ PASS | http://localhost:8080 정상 응답 |
| 컨테이너 목록 표시 | ✅ PASS | 4개 컨테이너 목록 및 상태 표시 |
| WebSocket 실시간 메트릭 | ✅ PASS | CPU%, 메모리% 갱신 확인 |
| 컨테이너 종료 이메일 알림 | ✅ PASS | Mailhog에서 [DOWN] 이메일 수신 확인 |
| 이메일 중복 방지 | ✅ PASS | DEDUP_WINDOW_SECONDS=30 설정 적용 |

---

## Phase 2: 규칙 기반 자가치유

| 항목 | 결과 | 비고 |
|------|------|------|
| 충돌 감지 | ✅ PASS | Docker 이벤트 즉시 감지 |
| 자동 재시작 | ✅ PASS | 자가치유 완료: exercise-auth 로그 다수 확인 |
| 라벨 기반 설정 | ✅ PASS | max-restarts=5, restart-delay=5s 적용 |
| 재시작 이메일 알림 | ✅ PASS | 재시작마다 알림 전송 완료 |
| 자가치유 제외 컨테이너 | ✅ PASS | docker-monitor, mailhog: self-healing.enabled=false |

```
[로그 근거]
자가치유 시작: exercise-auth (규칙: label-based)
이메일 전송 완료: exercise-auth
자가치유 완료: exercise-auth
```

---

## Phase 3: AI 사후 분석

| 항목 | 결과 | 비고 |
|------|------|------|
| 충돌 감지 → Incident 리포트 생성 | ✅ PASS | /incidents 페이지에 리포트 생성 확인 |
| AI 분석 완료 | ❌ BLOCKED | Gemini: 429 rate limit / Anthropic: 크레딧 부족 |

**미완료 사유**: 무료 Gemini API rate limit(15 RPM) 초과 및 Anthropic 크레딧 소진.
리포트 생성·트리거 파이프라인은 정상 동작, AI 응답 단계에서 외부 API 문제로 중단.

---

## Phase 5.1: GHCR 이미지 자동 업데이트

| 항목 | 결과 | 비고 |
|------|------|------|
| GHCR digest 폴링 | ✅ PASS | 60초 간격 정상 동작 |
| exercise-auth 새 digest 감지 | ✅ PASS | sha256:4f83c3f → sha256:758c0f5a |
| schedule-diary 새 digest 감지 | ✅ PASS | sha256:85d9a2f → sha256:a869e0eb |
| Rolling Update 자동 실행 | ✅ PASS | 성공=1, 실패=0 (양쪽 모두) |

```
[로그 근거]
새 이미지 감지: exercise-auth (sha256:4f83c3f63153... → sha256:758c0f5a669a...)
Rolling Update 완료: 성공=1, 실패=0

새 이미지 감지: schedule-diary (sha256:85d9a2f67990... → sha256:a869e0ebc49d...)
Rolling Update 완료: 성공=1, 실패=0
```

---

## Phase 5.2: Health Check Probe

| 항목 | 결과 | 비고 |
|------|------|------|
| TCP probe 동작 | ✅ PASS | exercise-auth :8080, schedule-diary :8080 |
| 연속 실패 감지 | ✅ PASS | 3회 연속 실패 시 재시작 트리거 |
| 재시작 트리거 | ✅ PASS | [Liveness] → 재시작 트리거 로그 확인 |

---

## Phase 5.3: Desired State

| 항목 | 결과 | 비고 |
|------|------|------|
| 설정 구성 | ✅ PASS | docker/test-config/application.yml 적용 |
| 컨테이너 삭제 후 자동 재생성 | ⏭️ 미실행 | 별도 시나리오 테스트 필요 |

---

## Phase 5.4: 다중 노드 스케줄링

| 항목 | 결과 | 비고 |
|------|------|------|
| 전체 | ⏭️ 제외 | 단일 호스트 환경 — 다중 Docker 호스트 필요 |

---

## Phase 5.5: 배포 전략

| 항목 | 결과 | 비고 |
|------|------|------|
| Rolling / Blue-Green / Canary | ⏭️ 미실행 | Phase 5.1 Rolling Update로 기본 동작 검증됨 |

---

## 버그 수정 내역

| 파일 | 수정 내용 |
|------|----------|
| `GhcrClient.java` | GHCR OAuth2 토큰 교환 구현 (PAT 직접 사용 시 403 → 토큰 교환 후 200) |
| `HealthCheckScheduler.java` | Docker Compose 네트워크에서 IP 빈 문자열 반환 시 컨테이너 이름으로 폴백 |
| `MetricsCsvExporterTest.java` | 하드코딩된 과거 날짜를 `now().minusHours()`로 수정 |
| `GeminiAiClient.java` | 모델 `gemini-1.5-flash` → `gemini-2.0-flash` (구 모델 404) |

---

## 잔여 과제

- [ ] Phase 3 AI 분석 — Gemini API 또는 Anthropic 크레딧 확보 후 재테스트
- [ ] Phase 5.3 Desired State — `docker stop && docker rm schedule-diary` 후 자동 재생성 확인
- [ ] Phase 5.5 배포 전략 — Blue-Green, Canary REST API 직접 호출 테스트
