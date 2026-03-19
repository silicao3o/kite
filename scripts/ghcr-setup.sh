#!/bin/bash
# ──────────────────────────────────────────────────────────────────
#  GHCR 이미지 빌드 & 푸시 스크립트
#  실행: bash scripts/ghcr-setup.sh
# ──────────────────────────────────────────────────────────────────
set -e

GITHUB_USER="silicao3o"
EXERCISE_AUTH_PATH="/Users/silica/IdeaProjects/exercise-auth"
SCHEDULE_DIARY_PATH="/Users/silica/schedule-diary"

# .env.test 에서 GHCR_TOKEN 로드
if [ -f "$(dirname "$0")/../.env.test" ]; then
  export $(grep -v '^#' "$(dirname "$0")/../.env.test" | xargs)
fi

if [ -z "$GHCR_TOKEN" ]; then
  echo "❌ GHCR_TOKEN 이 설정되지 않았습니다."
  echo "   .env.test 파일에 GHCR_TOKEN=ghp_... 를 추가하세요."
  exit 1
fi

echo "=== GHCR 로그인 ==="
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GITHUB_USER" --password-stdin

# ── exercise-auth 빌드 & 푸시 ──────────────────────────────────
echo ""
echo "=== exercise-auth 빌드 중... ==="
docker build \
  --platform linux/amd64 \
  -t ghcr.io/$GITHUB_USER/exercise-auth:latest \
  "$EXERCISE_AUTH_PATH"

echo "=== exercise-auth 푸시 중... ==="
docker push ghcr.io/$GITHUB_USER/exercise-auth:latest
echo "✅ exercise-auth 완료: ghcr.io/$GITHUB_USER/exercise-auth:latest"

# ── schedule-diary 빌드 & 푸시 ────────────────────────────────
echo ""
echo "=== schedule-diary 빌드 중... ==="
docker build \
  --platform linux/amd64 \
  -t ghcr.io/$GITHUB_USER/schedule-diary:latest \
  "$SCHEDULE_DIARY_PATH"

echo "=== schedule-diary 푸시 중... ==="
docker push ghcr.io/$GITHUB_USER/schedule-diary:latest
echo "✅ schedule-diary 완료: ghcr.io/$GITHUB_USER/schedule-diary:latest"

echo ""
echo "=== 모든 이미지 푸시 완료 ==="
echo "  ghcr.io/$GITHUB_USER/exercise-auth:latest"
echo "  ghcr.io/$GITHUB_USER/schedule-diary:latest"
echo ""
echo "다음 단계:"
echo "  docker compose -f docker-compose.test.yml --env-file .env.test up --build"
