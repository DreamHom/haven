# All-in-one Dockerfile — Railway / single-service deploys.
#
# Bundles Postgres 16 (with pgvector for Flyway V38) + the Spring Boot app
# into one image so a platform that runs ONE container per service (Railway,
# Heroku, Fly with single-process apps) can host the whole thing.
#
# Trade-offs vs. running Postgres as a separate Railway service:
#   - Container restart = data loss UNLESS you mount a volume at /var/lib/postgresql/data.
#   - No managed backups, no failover, no separate scaling.
#   - Image is larger (~700 MB) and needs more RAM (~700 MB-1 GB) at runtime.
#
# This is fine for a demo / submission deploy. For a real production rollout,
# split Postgres back out into its own service and use this repo's
# Dockerfile.app instead (slim app-only image, used by docker-compose.yml).

# ─── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cached dep-resolution layer — only invalidated when pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src src
RUN mvn -B -q -DskipTests package

# ─── Stage 2: runtime — pgvector Postgres + JRE 21 + app jar ────────────────
FROM pgvector/pgvector:pg16

# Install Eclipse Temurin JRE 21 from the Adoptium apt repo.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      wget gnupg apt-transport-https ca-certificates \
 && wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
      | gpg --dearmor > /usr/share/keyrings/adoptium.gpg \
 && echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] \
      https://packages.adoptium.net/artifactory/deb bookworm main" \
      > /etc/apt/sources.list.d/adoptium.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends temurin-21-jre \
 && apt-get purge -y --auto-remove wget gnupg apt-transport-https \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/haven-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker/start-allinone.sh /usr/local/bin/start-allinone.sh
RUN chmod +x /usr/local/bin/start-allinone.sh

# Defaults — these match the in-container Postgres so the app finds it at
# localhost:5432 with no extra config. Override at deploy time if needed.
# Kafka is dummied out — the outbox relay will retry-and-fail silently and
# the app still serves HTTP. Replace with a real broker URL when you add one.
ENV POSTGRES_DB=dreamhomes_haven \
    POSTGRES_USER=postgres \
    POSTGRES_PASSWORD=postgres \
    DB_HOST=localhost \
    DB_PORT=5432 \
    DB_NAME=dreamhomes_haven \
    DB_USERNAME=postgres \
    DB_PASSWORD=postgres \
    KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/start-allinone.sh"]
