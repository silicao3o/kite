#!/bin/bash
# ─────────────────────────────────────────────────────────────────
#  Docker Monitor 데모 스택 시작 스크립트
# ─────────────────────────────────────────────────────────────────

set -e

COMPOSE_FILE="docker-compose.demo.yml"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$ROOT_DIR"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Docker Monitor 데모 환경 시작"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 이미지 빌드 + 컨테이너 시작
echo "🔨 이미지 빌드 중... (첫 실행 시 수 분 소요)"
docker compose -f "$COMPOSE_FILE" up --build -d

echo ""
echo "⏳ 서비스 초기화 대기 중..."
sleep 5

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ 데모 스택 실행 완료"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  📊 Docker Monitor  →  http://localhost:8080"
echo "  🛒 Demo API        →  http://localhost:8081"
echo "  📧 Mailhog (이메일) →  http://localhost:8025"
echo ""
echo "  DB 접속 정보:"
echo "    PostgreSQL  localhost:5432  db=demo      user=demo      pw=demo123"
echo "    MySQL       localhost:3306  db=analytics user=analytics pw=analytics123"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  테스트 시나리오"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  [자가치유 테스트] demo-api를 강제 종료 (자동 재시작 확인)"
echo "    curl -X POST http://localhost:8081/api/products/crash"
echo ""
echo "  [상품 목록 조회]"
echo "    curl http://localhost:8081/api/products"
echo ""
echo "  [상품 등록]"
echo "    curl -X POST http://localhost:8081/api/products \\"
echo "      -H 'Content-Type: application/json' \\"
echo "      -d '{\"name\":\"테스트상품\",\"description\":\"설명\",\"price\":10000,\"stock\":5}'"
echo ""
echo "  [컨테이너 강제 종료 (docker-monitor에서 이벤트 감지)]"
echo "    docker stop demo-api"
echo ""
echo "  [전체 종료]"
echo "    docker compose -f docker-compose.demo.yml down"
echo ""
