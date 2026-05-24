# DreamHomes Haven — The Engine 🏠

> The secure backend powering DreamHomes. Every listing, every inspection request, every offer — it runs through here.

## What This Is

A Spring Boot 3.3 / Java 21 backend for the DreamHomes Moniepoint DreamDev capstone.
Owners list properties, applicants submit offers and request inspections, agents
handshake on assignments, admins moderate. Everything ships behind a stateless JWT,
with a Postgres + Flyway data layer and a transactional outbox writing to Kafka for
the two events that matter (`inspection.requested.v1`, `offer.submitted.v1`).

The codebase is organised **package-by-feature** in a single Maven module:
`com.dreamhomes.haven.<feature>` for each feature package, plus
`com.dreamhomes.haven.common.*` for cross-cutting infra (errors, validation,
rate limiting, the outbox pattern). Cross-feature reads happen through normal
service-to-service autowires, with two narrow `*Api` interfaces preserved where
they earn their keep (`NotificationApi`, `AdminAuditApi`).

For the design rationale + every "we chose X over Y" decision, see
[`docs/TRADEOFFS.md`](docs/TRADEOFFS.md). For the system overview at a snapshot, see
[`docs/STATE-OF-THE-SYSTEM.md`](docs/STATE-OF-THE-SYSTEM.md).

Promotions are modeled as a trust-preserving visibility layer: owners and agents can
request featured placement, admins approve or revoke it, public clients read dedicated
promotion feeds, and impression/click metrics track performance. A promoted listing or
agent still has to pass the normal safety checks before it appears publicly.

## Tech stack

- **Java 21** (LTS) · **Spring Boot 3.3.5** (Web, Security, Data JPA, Validation, Actuator)
- **PostgreSQL 16** + **Flyway** (V1..V42 migrations)
- **Spring Kafka** + **Apache Kafka 3.7 (KRaft)** — transactional outbox, dead-letter topic, partition-pinned `NewTopic` beans
- **JJWT 0.12.x** — JWT issuance + verification
- **Bucket4j** — in-process auth rate limiting
- **Micrometer** + **Prometheus** scrape endpoint
- **springdoc-openapi** — `/v3/api-docs` and `/scalar.html` served at runtime
- **AWS SDK v2 (S3)** — Cloudflare R2 image upload pipeline (pluggable `PhotoStorage` interface)
- **Spring Data JPA auditing** — automatic `@CreatedDate` / `@LastModifiedDate` on entities
- **JUnit 5** · **Mockito** · **Spring Security Test** · **Spring Kafka Test** (`@EmbeddedKafka`) · **Testcontainers** (Postgres) · **AssertJ**
- **Lombok** — boilerplate
- **Spring Boot DevTools** (dev only) — local hot reload
- **Maven** (single module)

## Project structure

```
haven/
├── pom.xml
├── docker-compose.yml
├── docs/                                     PRD, userflows, state-of-system, trade-offs, diagrams
└── src/
    ├── main/
    │   ├── java/com/dreamhomes/haven/
    │   │   ├── DreamhomesHavenApplication.java
    │   │   ├── common/                       cross-cutting: errors, validation,
    │   │   │   │                              rate limiting, request-id, web config
    │   │   │   └── outbox/                   transactional outbox infra
    │   │   ├── auth/                         AuthService, AuthController, JwtService,
    │   │   │                                  JwtAuthenticationFilter, MeController
    │   │   ├── user/                         User, AgentProfile, public profile, admin user ops
    │   │   ├── property/
    │   │   ├── listing/
    │   │   ├── photo/                        ListingPhoto
    │   │   ├── promotion/                    featured listings/agents, approval, metrics
    │   │   ├── engagement/                   ListingSave (saved listings)
    │   │   ├── agentlisting/                 owner ↔ agent handshake
    │   │   ├── comment/                      Q&A on listings
    │   │   ├── inspection/                   slots + requests
    │   │   ├── offer/                        offers + counter-offers
    │   │   ├── review/                       post-deal reviews
    │   │   ├── verification/                 4-track verification + admin decisions
    │   │   ├── notification/                 notification entity + Kafka listeners + NotificationApi
    │   │   └── admin/                        moderation + AdminAuditLog + AdminAuditApi
    │   └── resources/
    │       ├── application.yml
    │       ├── logback-spring.xml
    │       ├── db/migration/V1..V42.sql
    │       └── static/scalar.html
    └── test/
        ├── java/com/dreamhomes/haven/
        │   ├── support/                      AbstractPostgresIT, JwtTestSupport
        │   ├── auth/, user/, property/, …    tests next to the code they exercise
        │   └── ...
        └── resources/
```

## Architecture

Single module; package-by-feature; cross-feature reads via direct service autowires.
Two `*Api` interfaces survive because they're genuinely cross-cutting:

- **`NotificationApi`** — many features write notifications; the seam is worth its weight.
- **`AdminAuditApi`** — many features write audit log entries on admin actions.

Everything else inlines. `AuthService` autowires `UserRepository` directly;
`AdminUserService` does too. There is no build-time enforcement preventing a service
from reaching across feature lines — the codebase is small enough that code review
is the discipline. This is a deliberate scale-down from an earlier modular-monolith
experiment (33 Maven modules with `BannedDependencies`), reverted once it became
clear the build-time enforcement wasn't pulling its weight at our scale; see
[`docs/TRADEOFFS.md`](docs/TRADEOFFS.md).

Public promotion reads intentionally use dedicated placement endpoints rather than
flags on the broad listing/agent browse APIs. That keeps organic discovery and paid
visibility separate in the contract, makes "Featured" / "Sponsored" labeling explicit,
and gives admins a clean place to pause, revoke, and measure campaigns without changing
the core listing search semantics.

## Development philosophy

**TDD-first.** Tests are written before implementation. No exceptions. Every
architectural decision worth remembering lives in [`docs/TRADEOFFS.md`](docs/TRADEOFFS.md)
with a `why → cost → revisit` triplet.

## Getting started

Two paths: the **one-command Docker stack** (no Java/Maven on your machine —
fastest way for an evaluator to kick the tyres) or the **dev loop**
(`mvn spring-boot:run` against compose infra, hot reload via DevTools).

### Prerequisites — required by both paths

```bash
# (a) JWT — RS256 keypair. The app refuses to start if either is missing or
#     not a valid RSA key (>= 2048 bits).
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa     -in jwt-private.pem -pubout -out jwt-public.pem
export HAVEN_JWT_PRIVATE_KEY="$(cat jwt-private.pem)"
export HAVEN_JWT_PUBLIC_KEY="$(cat jwt-public.pem)"

# (b) Seeded platform admin — also required at startup, no defaults.
export ADMIN_EMAIL="admin@dreamhomes.local"
export ADMIN_PASSWORD_HASH="$(htpasswd -nbBC 10 '' 'ChangeMeNow!' | tail -c +2)"
```

### Path A: full stack via Docker Compose (one command)

Requires Docker (Docker Desktop on macOS). No local Java/Maven needed.

```bash
docker compose up --build
```

Brings up Postgres + Kafka + the app. App ready on `http://localhost:8080`
once the healthchecks pass. Stop with `Ctrl+C` (or `docker compose down`).
Wipe data with `docker compose down -v`.

### Path B: dev loop (hot reload)

Requires Java 21 + Maven on your machine. Faster iteration loop.

```bash
docker compose up -d postgres kafka     # infra only
mvn spring-boot:run                      # app on host
```

Postgres is on host port `5433` (container still listens on `5432` internally).
Kafka on host port `9092`.

### Deploying on Railway

Railway picks up the `Dockerfile` automatically and injects a dynamic `PORT` —
the app honors it via `server.port=${PORT:8080}` in `application.yml`. To deploy:

1. Add a **PostgreSQL** plugin to your Railway project. Set the app's `DB_HOST`,
   `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` from Railway's plugin variables.
2. Provision **Kafka** (Railway has no native Kafka; use Upstash, Aiven, or a
   self-hosted bitnami/kafka container template) and set `KAFKA_BOOTSTRAP_SERVERS`.
3. Set the JWT keypair + admin seed env vars (see prerequisites above) in the
   Railway service's Variables tab. PEMs are multi-line — paste with real newlines.
4. Set `CORS_ALLOWED_ORIGINS` to your frontend's deployed origin.
5. Configure Railway's healthcheck to hit `/actuator/health`.

## API documentation

- **OpenAPI 3 spec**: `GET /v3/api-docs`
- **Scalar UI** (interactive docs): `http://localhost:8080/scalar.html`
- **Health check** (anonymous): `GET /actuator/health` → `200 UP`
- **Prometheus scrape** (auth-gated): `GET /actuator/prometheus`

Every authenticated request stamps an `X-Request-ID` header (echoed from the client
if supplied, otherwise generated server-side) and threads it through MDC + structured
logs for end-to-end tracing.

## Running tests

```bash
mvn test     # surefire — unit tests
mvn verify   # surefire + failsafe — adds ITs (Testcontainers needs Docker running)
```

- Unit tests follow the `*Test` / `*Tests` naming convention and run via Surefire.
- Integration tests follow the `*IT` convention, extend `AbstractPostgresIT`
  (Testcontainers Postgres + Embedded Kafka, started once per JVM), and run via Failsafe.
- **Last saved reports:** 431 tests, 1 failure, 0 errors. The existing failure is
  `ListingPhotoIT`, where the expected local media URL prefix no longer matches the
  current R2-backed URL.

To run a single test:

```bash
mvn -Dtest=PublicUserProfileIT verify -DfailIfNoTests=false
```

## Reference docs

- [`docs/dreamhomes-prd.md`](docs/dreamhomes-prd.md) — product brief
- [`docs/dreamhomes-userflows.md`](docs/dreamhomes-userflows.md) — user journeys
- [`docs/STATE-OF-THE-SYSTEM.md`](docs/STATE-OF-THE-SYSTEM.md) — what's shipped, by phase
- [`docs/TRADEOFFS.md`](docs/TRADEOFFS.md) — every "X over Y" decision with revisit signals
- `docs/diagrams/` — drawio sources for the architecture, ERD, and flow diagrams

## License

See [LICENSE](LICENSE).

Built for Moniepoint DreamDev Bootcamp 2026.
