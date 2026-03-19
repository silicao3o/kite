#!/bin/bash

# Docker Monitor 테스트 시나리오 실행 스크립트
# 사용법: ./run-tests.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "  Docker Monitor 테스트 시나리오"
echo "=========================================="
echo ""

# 이전 테스트 컨테이너 정리
echo "[1/7] 이전 테스트 컨테이너 정리..."
docker-compose down 2>/dev/null || true
docker rm -f test-normal test-crash test-crashloop test-segfault test-kill test-oom 2>/dev/null || true
echo ""

# 시나리오 1: 정상 컨테이너 시작 후 수동 stop
echo "[2/7] 시나리오 1: 정상 컨테이너 (docker stop 테스트)"
echo "      - test-normal 컨테이너 시작"
docker-compose up -d test-normal
sleep 2
echo "      - docker stop 실행 (Exit Code: 143 예상)"
docker stop test-normal
echo "      → SIGTERM(143)으로 정상 종료됨"
echo ""

# 시나리오 2: 에러로 크래시
echo "[3/7] 시나리오 2: 에러로 크래시하는 컨테이너"
echo "      - test-crash 컨테이너 시작 (5초 후 크래시)"
docker-compose up -d test-crash
echo "      - 5초 대기..."
sleep 6
echo "      → Exit Code 1로 종료됨"
echo ""

# 시나리오 3: SegFault
echo "[4/7] 시나리오 3: Segmentation Fault"
echo "      - test-segfault 컨테이너 시작 (8초 후 SIGSEGV)"
docker-compose up -d test-segfault
echo "      - 9초 대기..."
sleep 10
echo "      → Exit Code 139 (SIGSEGV)로 종료됨"
echo ""

# 시나리오 4: docker kill 테스트
echo "[5/7] 시나리오 4: docker kill 테스트"
echo "      - test-kill 컨테이너 시작"
docker-compose up -d test-kill
sleep 2
echo "      - docker kill 실행 (Exit Code: 137 예상)"
docker kill test-kill
echo "      → SIGKILL(137)로 강제 종료됨"
echo ""

# 시나리오 5: CrashLoop (중복 방지 테스트)
echo "[6/7] 시나리오 5: CrashLoop (중복 방지 테스트)"
echo "      - test-crashloop 컨테이너 시작 (3초마다 재시작)"
echo "      - 10초간 관찰 (3~4회 크래시 예상)"
docker-compose up -d test-crashloop
sleep 10
echo "      - test-crashloop 중지"
docker-compose stop test-crashloop
echo "      → 중복 방지로 첫 번째 알림만 전송되어야 함"
echo ""

# 시나리오 6: OOM (선택적)
echo "[7/7] 시나리오 6: OOM Killer 테스트 (선택적)"
echo "      - test-oom 컨테이너 시작 (메모리 10MB 제한)"
docker-compose up -d test-oom 2>/dev/null || echo "      ⚠ OOM 테스트 스킵 (mem_limit 미지원 환경)"
sleep 5
echo ""

# 정리
echo "=========================================="
echo "  테스트 완료"
echo "=========================================="
echo ""
echo "테스트 컨테이너 상태:"
docker ps -a --filter "name=test-" --format "table {{.Names}}\t{{.Status}}\t{{.State}}"
echo ""
echo "컨테이너 정리: docker-compose down"
