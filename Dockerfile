# syntax=docker/dockerfile:1
# Builds the whole app (Angular inside the Spring Boot jar) into one image that listens on 8080.
# On the server, compose.prod.yaml builds it; see deploy/README.md.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# The Maven cache survives between builds (BuildKit cache mount), so a deploy only downloads
# what changed. frontend-maven-plugin installs Node into frontend/target itself.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /src/backend/target/tournament-leaderboard.jar app.jar
USER app
EXPOSE 8080
# Ready means the app has started and can reach its database; `docker compose up --wait` uses it.
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=5 \
  CMD ["sh", "-c", "wget -qO- http://localhost:8080/actuator/health/readiness > /dev/null || exit 1"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
