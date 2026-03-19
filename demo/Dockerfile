# ── 1단계: 빌드 ──────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 의존성 캐시 레이어 분리
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 소스 빌드
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── 2단계: 런타임 ─────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# bash 필요 (wait-for-db 스크립트용)
RUN apt-get update && apt-get install -y --no-install-recommends netcat-openbsd && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

# DB가 준비될 때까지 대기 후 앱 실행
ENTRYPOINT ["sh", "-c", "\
  echo '⏳ PostgreSQL 연결 대기 중 (${DB_HOST}:${DB_PORT:-5432})...'; \
  until nc -z ${DB_HOST:-postgres} ${DB_PORT:-5432}; do \
    sleep 1; \
  done; \
  echo '✅ PostgreSQL 준비 완료! 앱 시작...'; \
  exec java -Xmx256m -XX:+UseG1GC -jar app.jar"]
