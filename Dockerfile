# ============================================================
# Multi-stage Dockerfile for GIMI CI/CD Platform
# Targets: server, worker
# ============================================================

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY gimi-core/pom.xml gimi-core/pom.xml
COPY gimi-engine/pom.xml gimi-engine/pom.xml
COPY gimi-server/pom.xml gimi-server/pom.xml
COPY gimi-worker/pom.xml gimi-worker/pom.xml
COPY gimi-cli/pom.xml gimi-cli/pom.xml
RUN mvn dependency:go-offline -pl gimi-server,gimi-worker -am -q 2>/dev/null || true
COPY gimi-core/src gimi-core/src
COPY gimi-engine/src gimi-engine/src
COPY gimi-server/src gimi-server/src
COPY gimi-worker/src gimi-worker/src
COPY gimi-cli/src gimi-cli/src
RUN mvn package -DskipTests -pl gimi-server,gimi-worker -am -q

# --- Server runtime ---
FROM eclipse-temurin:21-jre-alpine AS server
RUN addgroup -S gimi && adduser -S gimi -G gimi
WORKDIR /app
COPY --from=build /build/gimi-server/target/*.jar app.jar
COPY pipelines/ pipelines/
USER gimi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# --- Worker runtime ---
FROM eclipse-temurin:21-jre-alpine AS worker
RUN addgroup -S gimi && adduser -S gimi -G gimi
WORKDIR /app
COPY --from=build /build/gimi-worker/target/*.jar app.jar
USER gimi
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
