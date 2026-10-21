#!/usr/bin/env bash
# アプリケーションのビルド、単体テスト、アーキテクチャのルールの検査を実行する。
# リポジトリのルートで、テストを実行する環境のコンテナの中で実行する。受け入れテストは実行しない。
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

echo "==> フロントエンド: 依存関係のインストール"
(cd app/frontend && npm ci)

echo "==> フロントエンド: Lint と整形の検査"
(cd app/frontend && npm run lint && npm run format:check)

echo "==> フロントエンド: 単体テスト"
(cd app/frontend && npm test)

echo "==> フロントエンド: ビルド"
(cd app/frontend && npm run build)

echo "==> バックエンド: ビルド、整形の検査、単体テスト"
(cd app/backend && ./gradlew --no-daemon build)

echo "==> アーキテクチャのルール: TypeScript (dependency-cruiser)"
(cd architecture-rules && npm ci)
architecture-rules/node_modules/.bin/depcruise \
  --config architecture-rules/dependency-cruiser.cjs \
  app/frontend/src

echo "==> アーキテクチャのルール: Java (ArchUnit)"
(cd architecture-rules/archunit \
  && ARCH_CLASSES_DIR="$ROOT/app/backend/build/classes/java/main" ./gradlew --no-daemon test)

echo "==> すべて成功しました"
