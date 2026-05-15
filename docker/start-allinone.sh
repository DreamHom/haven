#!/bin/bash
# Bundle entrypoint — boots Postgres + the Spring Boot app inside one container.
# Used by the all-in-one Dockerfile (Railway / single-service deploys).
#
# Postgres runs in the background via its standard docker-entrypoint (which
# handles first-boot initdb + creating POSTGRES_DB/USER from env). The Java
# app then runs in the foreground as PID-equivalent so signals propagate and
# the container exits when the JVM does.
set -e

echo "[boot] starting postgres in background…"
docker-entrypoint.sh postgres &

echo "[boot] waiting for postgres to accept connections…"
for i in {1..60}; do
  if pg_isready -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-dreamhomes_haven}" -h localhost >/dev/null 2>&1; then
    echo "[boot] postgres ready after ${i}s."
    break
  fi
  sleep 1
done

if ! pg_isready -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-dreamhomes_haven}" -h localhost >/dev/null 2>&1; then
  echo "[boot] postgres failed to come up within 60s — aborting." >&2
  exit 1
fi

echo "[boot] starting app on port ${PORT:-8080}…"
exec java ${JAVA_TOOL_OPTIONS:-} -jar /app/app.jar --server.port=${PORT:-8080}
