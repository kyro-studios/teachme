# teachme アプリケーション

AI 生徒育成型・対話学習サービスのアプリケーションです。構成の詳細は [`docs/architecture.md`](../docs/architecture.md) を参照してください。

```text
app/
├── frontend/   # React の SPA (Vite、Tailwind CSS、Vitest、Oxc)
└── backend/    # Javalin (Gradle、JUnit、Spotless)
```

## 開発

| 目的 | コマンド |
| --- | --- |
| フロントエンドの開発サーバー (5173。`/api` はバックエンドの 8080 へ中継) | `cd app/frontend && npm run dev` |
| バックエンドの起動 (8080。`AI_MODE` を指定しなければ `fake`) | `cd app/backend && ./gradlew run` |
| 画面と API を 1 つのオリジンで起動 | `PORT=4000 scripts/start-app.sh` |
| ビルド、単体テスト、アーキテクチャのルール | `scripts/test.sh` |

`scripts/test.sh` は、テストを実行する環境のコンテナ (`scripts/test.Dockerfile`) の中で実行します。

## 本番のイメージ

`app/Dockerfile` で、フロントエンドのビルド、バックエンドのビルド、実行用のイメージの順に作ります。バックエンドが `/api` と静的ファイルを返します。

```bash
docker build -f app/Dockerfile -t teachme app
```
