# DreamHomes Haven — The Engine 🏠

> The secure backend powering DreamHomes. Every listing, every inspection request, every offer — it runs through here.

## What This Is

A Spring Boot 3.3 / Java 21 backend for the DreamHomes Moniepoint DreamDev capstone.
Owners list properties, applicants submit offers and request inspections, agents
handshake on assignments, admins moderate. Everything ships behind a stateless JWT,
with a Postgres + Flyway data layer and a transactional outbox writing to Kafka for
the two events that matter (`inspection.requested.v1`, `offer.submitted.v1`).

The codebase is organised **package-by-feature** in a single Maven module:
`com.dreamhomes.haven.<feature>` for each of the 14 features, plus
`com.dreamhomes.haven.common.*` for cross-cutting infra (errors, validation,
rate limiting, the outbox pattern). Cross-feature reads happen through normal
service-to-service autowires, with two narrow `*Api` interfaces preserved where
they earn their keep (`NotificationApi`, `AdminAuditApi`).

For the design rationale + every "we chose X over Y" decision, see
[`docs/TRADEOFFS.md`](docs/TRADEOFFS.md). For the system overview at a snapshot, see
[`docs/STATE-OF-THE-SYSTEM.md`](docs/STATE-OF-THE-SYSTEM.md).

## Tech stack

- **Java 21** (LTS) · **Spring Boot 3.3.5** (Web, Security, Data JPA, Validation, Actuator)
- **PostgreSQL 16** + **Flyway** (V1..V18 migrations)
- **Spring Kafka** + **Apache Kafka 3.7 (KRaft)** — transactional outbox, dead-letter topic
- **JJWT 0.12.x** — JWT issuance + verification
- **Bucket4j** — in-process auth rate limiting
- **Micrometer** + **Prometheus** scrape endpoint
- **springdoc-openapi** — `/v3/api-docs` and `/scalar.html` served at runtime
- **JUnit 5** · **Mockito** · **Spring Security Test** · **Spring Kafka Test** (`@EmbeddedKafka`) · **Testcontainers** (Postgres) · **AssertJ**
- **Lombok** — boilerplate
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
    │       ├── db/migration/V1..V18.sql
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

## Development philosophy

**TDD-first.** Tests are written before implementation. No exceptions. Every
architectural decision worth remembering lives in [`docs/TRADEOFFS.md`](docs/TRADEOFFS.md)
with a `why → cost → revisit` triplet.

## Getting started

Requires Java 21 and Docker (Docker Desktop on macOS).

```bash
# 1. Start local infra (Postgres + Kafka, persistent volumes)
docker compose up -d

# 2. Configure environment.
#    JWT_SECRET is required (no fallback) and must be >= 32 bytes. The app refuses
#    to start if it looks like a placeholder ("change-me", "DEV_ONLY", etc.).
export JWT_SECRET="$(openssl rand -hex 32)"

# 3. Run the app
mvn spring-boot:run
```

Stop infra with `docker compose down`. Wipe data with `docker compose down -v`.

The app boots on `http://localhost:8080`. Postgres runs on host port `5433` (the
container still listens on `5432` internally).

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
- **Total today: 389 tests, 0 failures, 0 errors.** ~3 minutes wall-clock.

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
