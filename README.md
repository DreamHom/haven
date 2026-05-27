# DreamHomes Haven 🏠

> Backend for **DreamHomes** — a Lagos-focused real-estate marketplace where verified listings, verified agents, and verified property documents are the default, not the exception.

**Live:** API at [`haven.dreamhomes.today`](https://haven.dreamhomes.today) · UI at [`dreamhomes.today`](https://www.dreamhomes.today) · API explorer at [`/scalar.html`](https://haven.dreamhomes.today/scalar.html) · Health at [`/actuator/health`](https://haven.dreamhomes.today/actuator/health)

---

## Why it exists

Nigerian property rentals run on trust friction — fake listings, ghost agents, after-the-fact "C of O issues". DreamHomes flips the trust default: identity, documents, and agent credentials are reviewed by admins before a listing reaches the public, and applicants see those signals on every card. Haven is the backend that enforces that contract — listings, inspections, offers, agent assignments, verifications, moderation, promotions, and a Dream AI assistant — behind a single Spring Boot service.

Built for the Moniepoint DreamDev Bootcamp 2026 capstone.

---

## Architecture in one picture

```
       ┌──────────────────────────┐
       │   Vista (Next.js, SSR)   │
       │   vista.dreamhomes.today │
       └────────────┬─────────────┘
                    │ HTTPS (JWT)
                    ▼
   ┌───────────────────────────────────────────────────┐
   │   Haven — Spring Boot 3.3 / Java 21               │
   │   haven.dreamhomes.today                          │
   │                                                   │
   │   Controllers → Services → Repositories           │
   │   Cross-cutting: JWT, rate limit, ProblemDetail,  │
   │   transactional outbox, MDC traceId               │
   └──┬───────────┬──────────────┬──────────────┬──────┘
      ▼           ▼              ▼              ▼
   Postgres   Kafka          Cloudflare R2   Anthropic
   +pgvector  (transactional  (photo bytes)  (Dream AI)
              outbox)
```

Two deployable shapes share the same jar — `aws` profile (RDS + MSK in production) and `railway` profile (bundled Postgres + Confluent Cloud as fallback / local dev). One codebase, config-driven swap.

---

## Quickstart

### Option A — full stack via Docker (no Java/Maven on your machine)

```bash
docker compose up --build
# App ready at http://localhost:8080
# Postgres on host port 5433, Kafka on 9092
```

### Option B — dev loop with hot reload

```bash
docker compose up -d postgres kafka    # infra only
mvn spring-boot:run                     # app on host
```

### Required env vars (no defaults — Spring fails fast on missing)

```bash
# JWT keypair (RS256, RSA ≥ 2048)
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
export HAVEN_JWT_PRIVATE_KEY="$(cat jwt-private.pem)"
export HAVEN_JWT_PUBLIC_KEY="$(cat jwt-public.pem)"

# Platform admin seed (Flyway V11 reads these)
export ADMIN_EMAIL="admin@dreamhomes.local"
export ADMIN_PASSWORD_HASH="$(htpasswd -nbBC 10 '' 'ChangeMeNow!' | tail -c +2)"
```

See [`.env.example`](.env.example) for the full set (R2 keys, Anthropic key, Kafka credentials, etc. — all optional with graceful degradation when unset).

### Run the tests

```bash
mvn test     # unit tests (~5 sec)
mvn verify   # + Testcontainers integration tests (needs Docker running, ~3 min)
```

---

## Codebase tour

Package-by-feature in a single Maven module. Each feature owns its own controller, service, repository, DTOs, and tests.

```
src/main/java/com/dreamhomes/haven/
├── auth/          JWT, register/login, refresh tokens, /me
├── user/          profiles, role, agent profile, public discovery
├── property/      physical asset (one per real-world building)
├── listing/       offers/availabilities of properties (rent/sale, state machine)
├── photo/         R2-backed photo storage + pre-signed upload flow
├── inspection/    slots (GIST EXCLUDE non-overlap), requests, lifecycle
├── offer/         offers + counter-offer chain via parent_offer_id
├── comment/       Q&A on listings (soft-delete, threading-ready)
├── review/        post-deal reviews (deal-participant gate)
├── engagement/    saves (idempotent)
├── agentlisting/  owner ↔ agent assignments (request/accept/revoke)
├── verification/  4-track verification + admin decisions + mocked KYC providers
├── promotion/     featured placements, approval workflow, impression/click tracking
├── notification/  notification entity + Kafka listeners + SSE push
├── admin/         moderation queue, audit log, takedowns, suspensions
├── dreamai/       LLM-backed search + compare, provider abstractions
└── common/        cross-cutting: errors, validation, rate limit, outbox, kafka config
```

Cross-feature reads use direct service autowires. Two narrow `*Api` interfaces survive (`NotificationApi`, `AdminAuditApi`) where multiple features write through them. See [`docs/TRADEOFFS.md`](docs/TRADEOFFS.md) for why we reverted an earlier modular-monolith experiment.

---

## Key design decisions

The five calls that shaped the rest of the system. Full ledger of every "X over Y" choice lives in [`docs/TRADEOFFS.md`](docs/TRADEOFFS.md).

- **Listings go live the moment they're created — verification is non-blocking.** A new listing from an unverified owner is public immediately, with a visible "unverified" badge. The alternative (admin-approval queue) would have starved supply on day one. The cost: a fraudulent listing can be public for a window. We shrink that window with a community report endpoint + an admin moderation queue.

- **Kafka events keyed by `listingId`, not `inspectionId` / `offerId`.** Every event for one listing — request, decision, cancellation, offer — lands on the same partition, so consumers see them in production order. Trades horizontal throughput on viral listings for a coherent audit trail and consumer story. At our scale, correctness was free.

- **Slot non-overlap enforced by Postgres GIST EXCLUDE, not the service layer.** `inspection_slots` carries `EXCLUDE USING gist (listing_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)` (migration V8). Twenty concurrent inserts of the same slot → exactly one commits, nineteen rejected, atomically — verified by `InspectionSlotConcurrentIT`. The race condition can't exist, not just "we caught it."

- **Transactional outbox for every Kafka event.** Domain writes and outbox row commit in the same transaction; a 1-second poller ships outbox rows to Kafka. If the commit fails, no event fires. If Kafka is down, the row waits. No dual-write inconsistency between database state and the event stream.

- **Provider abstraction for the LLM, not a hard dependency on Anthropic.** `LlmProvider` interface + Spring `@ConditionalOnProperty` selection — Anthropic Haiku is the default, OpenAI / Gemini are wired and ready behind config. Same shape for embeddings (OpenAI today, Voyage / self-hosted scaffolded). Swap a vendor without touching call sites.

---

## Documentation map

| If you want to... | Read |
|---|---|
| Understand the product + personas | [`docs/dreamhomes-prd.md`](docs/dreamhomes-prd.md) · [`docs/users/`](docs/users/) |
| Get the architecture deep-dive (10 sessions) | [`docs/demo-prep/01-overview.md`](docs/demo-prep/01-overview.md) → [`10-cross-cutting.md`](docs/demo-prep/10-cross-cutting.md) |
| See every "why X over Y" decision | [`docs/TRADEOFFS.md`](docs/TRADEOFFS.md) |
| Know what's shipped vs deferred | [`docs/STATE-OF-THE-SYSTEM.md`](docs/STATE-OF-THE-SYSTEM.md) · [`docs/demo-prep/post-session-tasks.md`](docs/demo-prep/post-session-tasks.md) |
| Build the Vista frontend | [`docs/vista/cursor-handoff-prompt.md`](docs/vista/cursor-handoff-prompt.md) · [`docs/vista/vista-task-queue.md`](docs/vista/vista-task-queue.md) |
| Integrate the Promotion feature | [`docs/vista/promotions-frontend-prompt.md`](docs/vista/promotions-frontend-prompt.md) |
| Understand Dream AI | [`docs/dream-ai-capabilities.md`](docs/dream-ai-capabilities.md) · [`docs/dream-ai-providers.md`](docs/dream-ai-providers.md) |
| Drill into API contracts | [`/v3/api-docs`](https://haven.dreamhomes.today/v3/api-docs) or the rendered Scalar UI |

---

## Tech stack

| Layer | Choice |
|---|---|
| Language / framework | Java 21 · Spring Boot 3.3.5 |
| Persistence | PostgreSQL 16 + pgvector · Flyway (V1–V47) · Spring Data JPA |
| Messaging | Apache Kafka (KRaft) · Transactional outbox · Manual-ack consumer discipline |
| Auth | RS256 JWT (JJWT 0.12) · Refresh-token rotation with replay detection · Bucket4j rate limit |
| AI | Anthropic Claude Haiku (default) · OpenAI / Gemini scaffolded via provider abstraction |
| Embeddings | OpenAI text-embedding-3-small · pgvector cosine NN · Voyage / self-hosted scaffolded |
| Photos | Cloudflare R2 (S3-compatible) · AWS SDK v2 · pre-signed PUT + legacy multipart |
| Observability | Micrometer + Prometheus · Structured logs (SLF4J + Logback) · MDC traceId |
| Docs | springdoc-openapi → Scalar UI |
| Tests | JUnit 5 · Mockito · MockMvc · Spring Kafka Test · Testcontainers (Postgres) |
| Build | Maven (single module) · Lombok |

---

## Deployment

Production runs on **Railway** today (auto-deploy from `main`), with a parallel **AWS EKS** workflow in `.github/workflows/deploy-eks.yml` and an EC2 deploy at `.github/workflows/deploy-ec2.yml`. The all-in-one `Dockerfile` bundles Postgres+JRE+jar for the Railway path; `Dockerfile.app` is the app-only image for managed-Postgres deploys.

CI runs `mvn verify` with Testcontainers + JDK 21 on every push and PR (`.github/workflows/ci.yml`).

---

## The team

- **lukasio** — feature breadth: controllers, services, Dream AI, demo seeding, the test suite
- **Silas** — infrastructure: AWS migration (RDS + MSK), EC2 deploy, promotion feature, senior review on cross-cutting decisions

The "Silas the Integrator" entry in [`docs/users/silas-the-integrator.md`](docs/users/silas-the-integrator.md) is the same Silas — promoted to a first-class persona because his integrator lens catches gaps customer personas can't.

---

## License

See [LICENSE](LICENSE). Built for the Moniepoint DreamDev Bootcamp 2026.
