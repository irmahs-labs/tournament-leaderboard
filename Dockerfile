# syntax=docker/dockerfile:1
# Builds the whole app (Angular inside the Spring Boot jar) into one image that listens on 8080.
# On the server, compose.prod.yaml builds it; see deploy/README.md.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# The Maven and npm caches survive between builds (BuildKit cache mounts), so a deploy only
# downloads what changed. Tests, lint and typechecks are skipped: CI has already run them.
RUN --mount=type=cache,target=/root/.m2 --mount=type=cache,target=/root/.npm \
    ./mvnw -B -ntp package -DskipTests -Dfrontend.checks.skip

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /src/backend/target/tournament-leaderboard.jar app.jar
USER app
EXPOSE 8080
# Ready means the app has started and can reach its database; `docker compose up --wait` uses it.
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=5 \
  CMD ["wget", "-qO", "/dev/null", "http://localhost:8080/actuator/health/readiness"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
