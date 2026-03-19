#!/bin/bash
# ──────────────────────────────────────────────────────────────────
#  Phase 1~5 테스트 시나리오 스크립트
#  실행: bash scripts/test-scenarios.sh <시나리오번호>
#
#  사전 조건: docker-compose.test.yml 스택이 실행 중이어야 함
# ──────────────────────────────────────────────────────────────────

MONITOR_URL="http://localhost:8080"
EXERCISE_URL="http://localhost:8081"
DIARY_URL="http://localhost:8082"
MAIL_URL="http://localhost:8025"

print_header() {
  echo ""
  echo "══════════════════════════════════════════"
  echo "  $1"
  echo "══════════════════════════════════════════"
}

wait_sec() {
  echo "  ⏳ ${1}초 대기..."
  sleep "$1"
}

check_url() {
  local url=$1
  local desc=$2
  if curl -sf "$url" > /dev/null 2>&1; then
    echo "  ✅ $desc 응답 OK"
  else
    echo "  ❌ $desc 응답 없음"
  fi
}

# ────────────────────────────────────────────────────────────────
# Phase 1: 기본 모니터링 확인
# ────────────────────────────────────────────────────────────────
scenario_1_basic() {
  print_header "Phase 1: 기본 모니터링 확인"
  echo "  대시보드 접속 확인..."
  check_url "$MONITOR_URL" "Docker Monitor 대시보드"
  check_url "$EXERCISE_URL/actuator/health" "exercise-auth /actuator/health"
  check_url "$DIARY_URL" "schedule-diary"
  check_url "$MAIL_URL" "Mailhog UI"
  echo ""
  echo "  👉 브라우저에서 확인:"
  echo "     $MONITOR_URL         - 컨테이너 목록 (CPU/메모리/상태)"
  echo "     $MONITOR_URL/healing-logs - 자가치유 이력"
}

# ────────────────────────────────────────────────────────────────
# Phase 1: 이메일 알림 트리거 — 컨테이너 강제 종료
# ────────────────────────────────────────────────────────────────
scenario_1_alert() {
  print_header "Phase 1: 이메일 알림 — exercise-auth 강제 종료"
  echo "  ⚠️  exercise-auth 를 강제 종료합니다 (exit code 1)"
  docker kill --signal=SIGKILL exercise-auth 2>/dev/null || true
  echo "  컨테이너 종료됨. Docker Monitor 이벤트 감지 대기..."
  wait_sec 5
  echo ""
  echo "  👉 확인:"
  echo "     $MAIL_URL - [DOWN] 이메일 수신 확인"
  echo "     $MONITOR_URL - 컨테이너 상태 exited 표시"
}

# ────────────────────────────────────────────────────────────────
# Phase 2: 자가치유 테스트 — 컨테이너 충돌 → 자동 재시작
# ────────────────────────────────────────────────────────────────
scenario_2_selfheal() {
  print_header "Phase 2: 자가치유 — schedule-diary 충돌 후 자동 재시작"
  echo "  schedule-diary 프로세스를 죽입니다..."
  # 컨테이너 내부 프로세스만 죽임 (도커 컨테이너는 exit 상태가 됨)
  docker kill --signal=SIGKILL schedule-diary 2>/dev/null || true
  echo "  충돌 발생. 자가치유 감지 대기 (최대 15초)..."
  wait_sec 15
  echo ""
  echo "  자가치유 후 상태:"
  docker ps --filter name=schedule-diary --format "  컨테이너: {{.Names}} | 상태: {{.Status}}"
  echo ""
  echo "  👉 확인:"
  echo "     $MONITOR_URL/healing-logs - 자가치유 이력에 재시작 기록 확인"
  echo "     $MAIL_URL                 - [DOWN] + 재시작 알림 이메일 확인"
}

# ────────────────────────────────────────────────────────────────
# Phase 2: 최대 재시작 횟수 초과 테스트
# ────────────────────────────────────────────────────────────────
scenario_2_maxrestart() {
  print_header "Phase 2: 최대 재시작 초과 — 연속 충돌"
  echo "  ⚠️  schedule-diary 를 6회 연속 충돌시킵니다 (최대 5회 설정)"
  for i in {1..6}; do
    echo "  충돌 $i/6..."
    docker kill --signal=SIGKILL schedule-diary 2>/dev/null || true
    wait_sec 8
  done
  echo ""
  echo "  👉 확인:"
  echo "     $MAIL_URL - [MAX RESTARTS] 이메일 수신 확인"
  echo "     $MONITOR_URL/healing-logs - 자가치유 중단 기록 확인"
}

# ────────────────────────────────────────────────────────────────
# Phase 2: CPU 임계치 알림 테스트
# ────────────────────────────────────────────────────────────────
scenario_2_cpu() {
  print_header "Phase 2: CPU 임계치 알림 (임계치: 60%)"
  echo "  exercise-auth 컨테이너에서 CPU 부하 발생 (30초간)..."
  docker exec exercise-auth sh -c "for i in 1 2 3 4; do yes > /dev/null & done; sleep 30; kill %1 %2 %3 %4" &
  echo "  CPU 부하 실행 중. 메트릭 수집 주기(10초) 이후 알림 발생 예정..."
  wait_sec 20
  echo ""
  echo "  👉 확인:"
  echo "     $MAIL_URL - [CPU HIGH] 이메일 수신 확인"
  echo "     $MONITOR_URL - 대시보드 CPU% 수치 확인"
}

# ────────────────────────────────────────────────────────────────
# Phase 2: OOM 테스트
# ────────────────────────────────────────────────────────────────
scenario_2_oom() {
  print_header "Phase 2: OOM 테스트 (mem_limit: 512m)"
  echo "  ⚠️  exercise-auth 에서 메모리 고갈 유발..."
  echo "  (mem_limit 512m → OOM Killer 동작 예상)"
  docker exec exercise-auth sh -c \
    'python3 -c "import ctypes; ctypes.string_at(0, 1024*1024*600)" 2>/dev/null || \
     dd if=/dev/zero of=/tmp/bigfile bs=1M count=600 2>/dev/null' &
  wait_sec 15
  echo ""
  echo "  👉 확인:"
  echo "     $MAIL_URL - [OOM] 이메일 수신 확인"
  echo "     $MONITOR_URL - 컨테이너 OOM Killed 표시 확인"
}

# ────────────────────────────────────────────────────────────────
# Phase 3: AI 분석 — 충돌 후 AI 로그 분석 확인
# ────────────────────────────────────────────────────────────────
scenario_3_ai() {
  print_header "Phase 3: AI 사후 분석"
  echo "  exercise-auth 를 충돌시켜 AI 분석을 트리거합니다..."
  docker kill --signal=SIGKILL exercise-auth 2>/dev/null || true
  echo "  충돌 발생. AI 분석 완료까지 30~60초 소요..."
  wait_sec 15
  echo ""
  echo "  👉 확인:"
  echo "     $MONITOR_URL (Incident Reports 메뉴) - AI 분석 결과 및 재발 방지 제안"
  echo "     $MONITOR_URL (Suggestions 메뉴)      - 패턴 학습 제안 확인"
}

# ────────────────────────────────────────────────────────────────
# Phase 5.1: GHCR 자동 업데이트 테스트
# ────────────────────────────────────────────────────────────────
scenario_5_1_imagewatch() {
  print_header "Phase 5.1: GHCR 이미지 자동 업데이트"
  echo "  새 버전 빌드 후 GHCR 푸시 → Docker Monitor 감지 → Rolling Update"
  echo ""
  echo "  1. exercise-auth 소스에 사소한 변경 후 재빌드 & 푸시:"
  echo "     cd /Users/silica/IdeaProjects/exercise-auth"
  echo "     docker build --platform linux/amd64 -t ghcr.io/silicao3o/exercise-auth:latest ."
  echo "     docker push ghcr.io/silicao3o/exercise-auth:latest"
  echo ""
  echo "  2. Docker Monitor 가 60초 내 새 digest 감지 후 자동 업데이트"
  echo ""
  echo "  👉 확인:"
  echo "     $MONITOR_URL - 컨테이너 업데이트 중 상태 확인 (rolling restart)"
  echo "     docker logs docker-monitor | grep -i 'image\|update\|digest'"
}

# ────────────────────────────────────────────────────────────────
# Phase 5.2: Health Check Probe 테스트
# ────────────────────────────────────────────────────────────────
scenario_5_2_healthcheck() {
  print_header "Phase 5.2: Health Check Probe"
  echo "  exercise-auth 의 Health Check 를 실패하게 만들어 재시작 유도..."
  echo ""
  echo "  방법: /actuator/health 가 응답하지 않도록 포트를 막음"
  # iptables 로 내부 8080 포트 차단 (컨테이너 내부에서)
  docker exec exercise-auth sh -c \
    "iptables -A INPUT -p tcp --dport 8080 -j DROP 2>/dev/null || \
     apk add iptables -q 2>/dev/null && iptables -A INPUT -p tcp --dport 8080 -j DROP" \
    2>/dev/null || echo "  (iptables 불가 — 대신 프로세스를 일시정지합니다)"

  # 대안: SIGSTOP으로 프로세스 일시정지
  docker exec exercise-auth sh -c "kill -STOP 1" 2>/dev/null || true
  echo "  프로세스 일시정지. Health Check 실패 감지 대기 (45~60초)..."
  wait_sec 50
  echo ""
  echo "  👉 확인:"
  echo "     $MONITOR_URL/healing-logs - HealthCheck 트리거 재시작 기록"
  echo "     docker logs docker-monitor | grep -i 'liveness\|probe\|restart'"
}

# ────────────────────────────────────────────────────────────────
# Phase 5.3: Desired State 테스트 — 컨테이너 삭제 후 자동 재생성
# ────────────────────────────────────────────────────────────────
scenario_5_3_desiredstate() {
  print_header "Phase 5.3: Desired State — 컨테이너 삭제 후 자동 재생성"
  echo "  schedule-diary 컨테이너를 완전히 삭제합니다..."
  docker stop schedule-diary 2>/dev/null || true
  docker rm schedule-diary 2>/dev/null || true
  echo "  삭제 완료. Desired State Reconciler 가 30초 내 재생성 예정..."
  wait_sec 35
  echo ""
  echo "  재생성 후 컨테이너 상태:"
  docker ps --filter name=schedule-diary --format "  {{.Names}} | {{.Status}} | {{.Image}}"
  echo ""
  echo "  👉 확인:"
  echo "     $MONITOR_URL - schedule-diary 가 다시 running 상태인지 확인"
  echo "     docker logs docker-monitor | grep -i 'reconcil\|desired\|created'"
}

# ────────────────────────────────────────────────────────────────
# Phase 5.5: 배포 전략 테스트 — Rolling Update 수동 트리거
# ────────────────────────────────────────────────────────────────
scenario_5_5_deploy() {
  print_header "Phase 5.5: 배포 전략 — Rolling Update"
  echo "  새 이미지 태그를 생성해 Rolling Update 트리거..."
  echo ""
  echo "  방법 A — GHCR 새 이미지 푸시 (5.1 자동 업데이트 활용):"
  echo "    bash scripts/ghcr-setup.sh  # 재빌드 & 푸시 → 자동 감지"
  echo ""
  echo "  방법 B — Docker Monitor REST API 직접 호출:"
  echo "    curl -X POST $MONITOR_URL/api/deploy \\"
  echo "      -H 'Content-Type: application/json' \\"
  echo "      -d '{\"container\":\"exercise-auth\",\"strategy\":\"ROLLING\"}'"
  echo ""
  echo "  👉 확인:"
  echo "     $MONITOR_URL - 업데이트 중 컨테이너 상태 (일시적 재시작)"
  echo "     docker logs docker-monitor | grep -i 'rolling\|deploy\|update'"
}

# ────────────────────────────────────────────────────────────────
# 전체 스택 상태 확인
# ────────────────────────────────────────────────────────────────
scenario_status() {
  print_header "현재 스택 상태"
  echo "  실행 중인 컨테이너:"
  docker ps --format "  {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null
  echo ""
  echo "  서비스 접속:"
  check_url "$MONITOR_URL" "Docker Monitor"
  check_url "$EXERCISE_URL/actuator/health" "exercise-auth health"
  check_url "$DIARY_URL" "schedule-diary"
  check_url "$MAIL_URL" "Mailhog"
}

# ────────────────────────────────────────────────────────────────
# 메인
# ────────────────────────────────────────────────────────────────
case "${1:-help}" in
  status)         scenario_status ;;
  1-basic)        scenario_1_basic ;;
  1-alert)        scenario_1_alert ;;
  2-selfheal)     scenario_2_selfheal ;;
  2-maxrestart)   scenario_2_maxrestart ;;
  2-cpu)          scenario_2_cpu ;;
  2-oom)          scenario_2_oom ;;
  3-ai)           scenario_3_ai ;;
  5-1-imagewatch) scenario_5_1_imagewatch ;;
  5-2-healthcheck) scenario_5_2_healthcheck ;;
  5-3-desiredstate) scenario_5_3_desiredstate ;;
  5-5-deploy)     scenario_5_5_deploy ;;
  *)
    echo "사용법: bash scripts/test-scenarios.sh <시나리오>"
    echo ""
    echo "  status           현재 스택 상태 확인"
    echo ""
    echo "  --- Phase 1: 기본 모니터링 ---"
    echo "  1-basic          대시보드 접속 및 기본 상태 확인"
    echo "  1-alert          컨테이너 종료 → 이메일 알림 확인"
    echo ""
    echo "  --- Phase 2: 자가치유 ---"
    echo "  2-selfheal       충돌 → 자동 재시작"
    echo "  2-maxrestart     연속 충돌 → 최대 재시작 초과 알림"
    echo "  2-cpu            CPU 부하 → CPU 임계치 알림"
    echo "  2-oom            메모리 고갈 → OOM 알림"
    echo ""
    echo "  --- Phase 3: AI 분석 ---"
    echo "  3-ai             충돌 → AI 사후 분석 결과 확인"
    echo ""
    echo "  --- Phase 5: 오케스트레이션 ---"
    echo "  5-1-imagewatch   GHCR 이미지 업데이트 → 자동 롤링 업데이트"
    echo "  5-2-healthcheck  Health Probe 실패 → 재시작"
    echo "  5-3-desiredstate 컨테이너 삭제 → 자동 재생성"
    echo "  5-5-deploy       배포 전략 (Rolling Update) 시연"
    ;;
esac
