# Session 10 — Cross-cutting + Ops

## RFC 7807 ProblemDetail

Every error response across Haven follows RFC 7807. Content-Type is `application/problem+json`. The body always has:

```json
{
  "type": "https://...errors/.../<error-name>",
  "title": "Short human-readable summary",
  "status": 409,
  "detail": "Long human-readable explanation",
  ...optional fields specific to the error
}
```

Why: the frontend (Vista) does `switch (res.status)` AND can also inspect `type` to render a specific error message. No "something went wrong" toasts; every error has a stable type URI clients can branch on.

Implemented via a global `@RestControllerAdvice` that maps each domain exception (`OptimisticLockException`, `VerificationRoleMismatchException`, etc.) to a specific status + type. The type URI base is `haven.errors.type-base` config — points at the docs in the repo.

## Rate limiting

Two filters today:

- `AuthRateLimitFilter` — on `/api/auth/*` POSTs, 30/min/IP via Bucket4j. Blocks brute-force before bcrypt even runs.
- `DreamAiRateLimitFilter` — on `/api/dream-ai/suggestions` + `/turns/stream`, 30/min/user-or-IP.

Both return 429 + `Retry-After` + ProblemDetail body.

## Observability

Three layers:

**Structured logs.** SLF4J + Logback. Key cross-cutting fields populated as MDC: `traceId` (one UUID per request), `dreamAiUserId` (for Dream AI calls). Means a log query like "show me everything from traceId X" reconstructs a full request flow.

**Prometheus metrics.** `actuator/prometheus` exposes JVM, HTTP, JPA, and Kafka consumer metrics. Custom metrics: outbox depth (`OutboxMetrics`), outbox DLT count (`OutboxDltMetrics`), notification dispatch counts.

**Actuator endpoints.** `/actuator/health` for k8s/load-balancer liveness probes (anonymous). `/actuator/info`, `/actuator/prometheus` for monitoring (auth-gated).

## Flyway

Schema is versioned and forward-only. 41 migrations in `src/main/resources/db/migration/` (V1 through V41). Run automatically on startup. Each migration is idempotent and irrevocable — never modify an existing migration; always add a new one.

Why: deterministic schema across local, staging, production. No "works on my machine" because the migration ran in a different order.

## DemoDataSeeder

`@Component` + `@ConditionalOnProperty(haven.demo.auto-seed)`. Runs once on startup (idempotent — skips if `listingRepository.count() > 0`).

Seeds: 10 users (Amaka, Biodun, Emeka, Temi, Ngozi, Dayo, Adaeze, Babatunde, plus 2 more) all sharing the `Demo2026!` password, 8 properties + 8 listings, 8 verifications across all 4 types, 4 ACCEPTED agent assignments for Emeka, Ngozi's PENDING offer on Lekki, 2 admin audit log entries.

Built specifically because Railway's bundled-Postgres approach wiped the database on every redeploy. The seeder restores realistic demo state on each fresh boot.

## Docker — all-in-one image

`Dockerfile` is a multi-stage build:

- Stage 1: build the jar with Maven
- Stage 2: `pgvector/pgvector:pg16` base + JRE 21 + the jar + `docker/start-allinone.sh`

`start-allinone.sh` backgrounds Postgres via `docker-entrypoint.sh`, waits for `pg_isready`, then `exec`s the Java process with `$PORT`. Single container, one running process tree.

Trade-off: Postgres data lives in the container's writable layer with no Railway volume mounted. Redeploys wipe the database. Mitigated by `DemoDataSeeder`. The fix is to mount a Railway persistent volume at `/var/lib/postgresql/data` — listed in `docs/TRADEOFFS.md`.

## Deployment

Production today: Railway hosts the all-in-one container at `haven.dreamhomes.today`. Auto-deploys from `main` branch on push.

Migration to AWS (Silas-led) is documented but the current deploy target needs verification with Silas — see post-session-tasks Item 15.

## CI/CD via GitHub Actions

Two workflows in `.github/workflows/`:

- `ci.yml` — runs on every push/PR, executes `mvn verify` with Testcontainers + JDK 21. Concurrency control: cancels older runs on the same branch.
- `deploy-eks.yml` — auto-deploys to AWS EKS on push to main. Whether this is the active deploy or dead-letter needs Silas confirmation.

## OpenAPI / Scalar UI

springdoc auto-generates `GET /v3/api-docs` from controller annotations. Scalar renders it at `/scalar.html`. Every endpoint has class-level `@Tag`; the bigger plan to per-endpoint annotate (`@Operation`, `@ApiResponses`, `@ExampleObject`) is documented in `docs/api-restructure-tasklist.md` (Item 27 in post-session-tasks).

## Photos via R2

Cloudflare R2 (S3-compatible) hosts listing photos. Uploads proxy through Haven today (`R2PhotoStorage` uses the AWS SDK's `S3Client.putObject`). Read path is direct from R2's CDN — Haven never touches read bytes. The pre-signed upload path is scaffolded but not active (Item 2 in post-session-tasks).
