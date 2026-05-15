# Multi-stage build for Spring Boot 3.3 / Java 21.
#
# Stage 1 (build): Maven + JDK 21 image. POM copied + deps resolved as a
# separate layer from source — code-only changes skip the dep download because
# the Docker layer cache for the dep-resolution step still hits.
#
# Stage 2 (runtime): JRE-only base image — smaller surface, no Maven, no JDK.
# Runs as a non-root user. Honors `PORT` env var (Railway injects it) and falls
# back to 8080 for local docker-compose use.
#
# NOTE: deliberately not using BuildKit `--mount=type=cache` — Railway's builder
# doesn't accept it. Layer caching alone is enough.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1. Copy only the POM first and resolve deps. This layer is cached across
#    code-only rebuilds — only invalidated when pom.xml itself changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 2. Copy sources and build. Tests are skipped because they require a running
#    Postgres + embedded Kafka — CI runs them separately, the image only needs
#    the packaged jar.
COPY src src
RUN mvn -B -q -DskipTests package

# ---

FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root runtime user.
RUN groupadd --system --gid 1001 haven \
 && useradd  --system --uid 1001 --gid haven --shell /usr/sbin/nologin haven

COPY --from=build --chown=haven:haven /workspace/target/haven-0.0.1-SNAPSHOT.jar app.jar

USER haven

# Railway injects PORT at runtime; local docker-compose falls back to 8080.
# JVM flag set: respect container memory limits, prefer the G1 collector,
# log to stdout for the platform's log collector.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# `sh -c` so ${PORT:-8080} is expanded by the shell at container start.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_TOOL_OPTIONS -jar /app/app.jar --server.port=${PORT:-8080}"]
