# 本番のイメージ: フロントエンドのビルド → バックエンドのビルド → 実行
FROM node:22-slim AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npx vite build

FROM eclipse-temurin:21-jdk AS backend
WORKDIR /build
COPY backend/ ./
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /srv
COPY --from=backend /build/build/install/backend/ ./backend/
COPY --from=frontend /build/dist/ ./static/
ENV ENV=production \
    PORT=8080 \
    STATIC_DIR=/srv/static
EXPOSE 8080
CMD ["./backend/bin/backend"]
