#!/usr/bin/env bash
# 受け入れテストのために、画面と API を 1 つのオリジンで起動する。
# リポジトリのルートで、scripts/test.sh を実行した後の同じコンテナの中で実行する。
# SIGTERM で止められるまで、前面で動き続ける。
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
export PORT="${PORT:-4000}"

PG_CONTAINER="teachme-pg-$$"
BACKEND_PID=""

cleanup() {
  trap '' TERM INT
  if [ -n "$BACKEND_PID" ]; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 143' TERM INT

echo "==> フロントエンドをビルドする"
if [ ! -d app/frontend/node_modules ]; then
  (cd app/frontend && npm ci)
fi
(cd app/frontend && npx vite build)

echo "==> バックエンドをビルドする"
(cd app/backend && ./gradlew --no-daemon -q installDist)

echo "==> PostgreSQL を起動する"
docker run -d --rm --name "$PG_CONTAINER" \
  -e POSTGRES_USER=teachme -e POSTGRES_PASSWORD=teachme -e POSTGRES_DB=teachme \
  -p 127.0.0.1::5432 postgres:16-alpine >/dev/null
PG_PORT="$(docker port "$PG_CONTAINER" 5432/tcp | head -n1 | sed 's/.*://')"
for _ in $(seq 1 60); do
  if docker exec "$PG_CONTAINER" pg_isready -U teachme -d teachme >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "==> バックエンドを起動する (PORT=$PORT)"
DATABASE_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/teachme" \
DATABASE_USER=teachme \
DATABASE_PASSWORD=teachme \
AI_MODE=fake \
STATIC_DIR="$ROOT/app/frontend/dist" \
  app/backend/build/install/backend/bin/backend &
BACKEND_PID=$!
wait "$BACKEND_PID"
