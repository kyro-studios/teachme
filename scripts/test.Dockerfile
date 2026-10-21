# テストを実行する環境。ツールだけを入れ、リポジトリは実行時にマウントする
FROM eclipse-temurin:21-jdk

# Node.js 22 (LTS)。受け入れテストの Playwright も、このコンテナで実行する
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg git \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Docker の CLI (ホストの Docker のソケットを使って、Testcontainers と PostgreSQL を起動する)
COPY --from=docker:27-cli /usr/local/bin/docker /usr/local/bin/docker

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
