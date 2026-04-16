# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependency downloads separately from source compilation
COPY pom.xml .
RUN mvn dependency:go-offline -B \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.retryHandler.requestSentEnabled=true

COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8081

# docker-entrypoint.sh converts /run/secrets/* → env vars, then starts the JVM.
# Works transparently for both local (env vars) and prod (Docker secrets).
ENTRYPOINT ["docker-entrypoint.sh", "java", "-jar", "app.jar"]
