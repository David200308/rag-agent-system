# ── Stage 1: build ────────────────────────────────────────────────────────────
# Build context is the multi-module root (./backend) so Maven can see the parent POM.
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY agent-system-rest/pom.xml agent-system-rest/pom.xml
COPY agent-system-storage-inner/pom.xml agent-system-storage-inner/pom.xml
COPY agent-system-notification-consumer/pom.xml agent-system-notification-consumer/pom.xml
RUN mvn -B -pl agent-system-notification-consumer -am dependency:resolve dependency:resolve-plugins \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.retryHandler.requestSentEnabled=true

COPY agent-system-notification-consumer/src agent-system-notification-consumer/src
RUN mvn -pl agent-system-notification-consumer -am package -DskipTests -B

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/agent-system-notification-consumer/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8084

# docker-entrypoint.sh converts /run/secrets/* → env vars, then starts the JVM.
ENTRYPOINT ["docker-entrypoint.sh", "java", "-jar", "app.jar"]
