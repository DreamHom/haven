# Session 1 — Product + Architecture Overview

## Mental model (the one-paragraph version)

**DreamHomes** is a Nigeria-focused real-estate marketplace. Owners and developers list properties, applicants browse and engage (save, comment, request inspections, submit offers), agents represent listings on behalf of owners, and admins moderate the platform. **Haven** is the backend that powers all of this — a single Spring Boot service that owns identity, listings, inspections, offers, engagement, agent assignments, verifications, notifications, admin moderation, and a Dream AI assistant. Everything is exposed as a REST + SSE API consumed by the **Vista** frontend (Next.js, separate repo).

## The team

Two engineers built Haven:

- **You (lukasio)** — feature breadth: controllers, services, Dream AI, demo seeding, the bulk of the test suite.
- **Silas (senior backend)** — led infrastructure: the AWS migration (RDS + MSK), deployment hardening, senior review on cross-cutting decisions.

### Why Silas also exists as a *persona* (and why that's deliberate, not a mistake)

The codebase has 7 user personas — 6 customers (Amaka, Biodun, Emeka, Temi, Ngozi, Dayo) and one "**Silas the Integrator**" in `docs/users/silas-the-integrator.md`. **That's Silas the co-dev, captured as a persona on purpose.**

The reason: customer personas walk *business flows* — register, publish, offer, inspect, moderate. They never sit in the integrator's seat. They never ask "can I render this response without an N+1 fan-out?" or "does this PATCH have a matching GET so I can preload the form?" or "does the runtime status match the spec so my `switch (res.status)` actually fires?"

When we made Silas's lens a first-class persona, we caught gaps the customer flows hid:

- **Story 2** — every editable field needs a readable counterpart (caught settings fields being editable but not preloadable in PR #6).
- **Story 5** — validation failures returned 401 instead of 400 at runtime; no customer persona ever checked the status code (only Silas's "the spec said 400" did).
- **Story 3** — listing detail embeds `PropertySummary`, `assignedAgentId`, `pendingReportCount` so the listing card renders in **one call** instead of six.
- **Story 7** — sync notifications + SSE so the user never sees silence after a button press.

So when someone asks "Silas the integrator — that's not a dev, that's a persona, right?" the answer is: **same person, both lenses, intentional**. His concerns are first-class user stories alongside the customers' because the team treats integration friction as a real cost, not a footnote.

## Who uses it — the 7 personas

| Persona | Role | What they do |
| --- | --- | --- |
| **Amaka** | OWNER | Lagos landlord — lists her properties, manages inspections, decides on offers, assigns agents |
| **Biodun** | OWNER | Property developer — multi-unit projects, hires agents to handle traffic |
| **Emeka** | AGENT | Hustling agent — accepts assignments from owners, handles inspections + buyer questions |
| **Temi** | APPLICANT | First-time renter — browses, saves listings, books inspections, submits offers |
| **Ngozi** | APPLICANT | Skeptical buyer — reads reviews + verification badges before engaging |
| **Dayo** | ADMIN | Platform guardian — reviews verifications, takes down bad listings, suspends abusive users |
| **Silas** | INTEGRATOR | Senior backend dev + frontend integrator lens — caught the "renderable screen" gaps the customer personas couldn't |

> Tests reference these stories by ID (e.g. `Temi S8 = submits an offer`, `Silas S5 = errors the UI can branch on`) so the test suite ties directly to documented user flows.

## Architecture at a glance

```
                ┌──────────────────────────┐
                │   Vista (Next.js, SSR)   │
                │   vista.dreamhomes.today │
                └────────────┬─────────────┘
                             │ HTTPS (JWT)
                             ▼
   ┌─────────────────────────────────────────────────────────┐
   │   Haven — Spring Boot 3.3 / Java 21 / single module     │
   │   haven.dreamhomes.today                                │
   │                                                         │
   │   Controllers → Services → Repositories                 │
   │   Cross-cutting: JWT auth, rate limit, ProblemDetail,   │
   │   transactional outbox, MDC traceId                     │
   └──┬──────────────┬─────────────┬──────────────┬──────────┘
      │              │             │              │
      ▼              ▼             ▼              ▼
  ┌────────┐   ┌──────────┐   ┌────────┐   ┌───────────┐
  │   AWS  │   │   AWS    │   │   R2   │   │ Anthropic │
  │   RDS  │   │   MSK    │   │(photos)│   │  (Haiku)  │
  │(Postgres│  │  (Kafka) │   │S3-compat│  │ Optional  │
  │+pgvector│  │          │   │         │  │           │
  └────────┘   └──────────┘   └────────┘   └───────────┘
   profile:     profile:
    aws          aws

   Alt profile (railway): bundled Postgres + Confluent Cloud
   — kept as fallback for local dev + demo backup
```

**Two deployable shapes**, toggled via Spring profile:
- `aws` (production) — RDS + MSK, Silas-led migration
- `railway` (fallback / local) — bundled Postgres in the container + Confluent Cloud Kafka

Both profiles share the same application code; only `application-<profile>.yml` differs.

## Tech stack — what + why

| Layer | Choice | Why |
| --- | --- | --- |
| **Language** | Java 21 | LTS; records + pattern matching; what the bootcamp expects |
| **Framework** | Spring Boot 3.3.5 | Batteries-included; security + JPA + Kafka + actuator |
| **Build** | Maven (single module) | Simpler than multi-module for project this size |
| **DB (prod)** | **AWS RDS Postgres + pgvector** | Managed, backed up, durable; pgvector for Dream AI nearest-neighbour search |
| **DB (fallback)** | Bundled Postgres in container | Demo backup; runs anywhere |
| **Migrations** | Flyway (V1–V40) | Versioned schema; forward-only; checked into git |
| **Auth** | RS256 JWT (asymmetric) | Verify without sharing secrets; supports multi-service future |
| **Messaging (prod)** | **AWS MSK + transactional outbox** | Managed Kafka; same outbox guarantees |
| **Messaging (fallback)** | Confluent Cloud Kafka | Same protocol, different host |
| **AI** | Anthropic (Claude Haiku) | Cheap + fast for ranking JSON; bounded-output design tolerates a small model |
| **Photos** | Cloudflare R2 (S3-compatible) | Cheap egress; pre-signed URLs |
| **Docs** | springdoc OpenAPI → Scalar UI | Auto-generated from annotations |
| **Tests** | JUnit 5 + Testcontainers + MockMvc | Real Postgres + Kafka in IT, no fakes |
| **Deployment** | AWS (prod) / Railway (alt) | See below |

## Deployment shape

### Production (today): AWS, Silas-led migration

- **Spring profile**: `aws`
- **Compute**: [TBD — confirm with Silas: ECS Fargate? EKS? EC2?]
- **Database**: AWS RDS for Postgres 16 with the pgvector extension; persistent backups
- **Messaging**: AWS MSK (managed Kafka)
- **Photos**: Cloudflare R2 (unchanged)
- **AI**: Anthropic API (unchanged)
- **Domain**: `haven.dreamhomes.today`
- **Why**: durability (no DB wipes on redeploy), production-grade managed services, easier to scale, free-tier no longer the constraint

### Fallback (alternative): Railway all-in-one

- **Spring profile**: `railway`
- **One Docker image** bundles `pgvector/pgvector:pg16` + JRE 21 + jar + startup script
- **Confluent Cloud** for Kafka (SASL_SSL)
- **Kept around** for local dev, demo backup, and to demonstrate "this is how it worked on free tier"

## Repo layout (the bird's-eye view)

```
haven/
├── src/main/java/com/dreamhomes/haven/
│   ├── auth/ user/ property/ listing/ inspection/ offer/
│   ├── comment/ review/ engagement/ agentlisting/
│   ├── verification/ notification/ admin/ dreamai/
│   ├── common/         # Config, security, errors, ratelimit, kafka outbox, seed
│   └── HavenApplication.java
├── src/main/resources/
│   ├── application.yml             # Shared config + profile activation
│   ├── application-aws.yml         # [pending Silas sync] AWS RDS + MSK
│   ├── application-railway.yml     # [pending Silas sync] bundled PG + Confluent
│   └── db/migration/               # V1__ → V40__ Flyway scripts
├── src/test/java/...               # *Test = unit, *IT = Testcontainers integration
├── docs/                           # PRD, user flows, trade-offs, AI docs, demo prep
├── docker/                         # Dockerfile + start-allinone.sh (fallback)
└── audit/bruno/                    # Bruno HTTP collections (manual testing + seed)
```

## Numbers worth knowing

- **47 endpoints** across **15 tag groups**
- **40 Flyway migrations** (V1 → V40)
- **389 tests** passing (unit + Testcontainers IT)
- **~2 weeks** of build time, **2-engineer team** (lukasio + Silas)

## Decisions + trade-offs (the big ones)

1. **Monolith over microservices** — cohesive bounded context; faster to ship; can split later if a surface needs independent scaling.
2. **Transactional outbox over direct Kafka writes** — guarantees "if DB commit succeeded, event publishes" without two-phase commit. Cost: one extra table + background publisher.
3. **pgvector over a dedicated vector DB** — a few thousand listings fit in Postgres easily; no new bill, no new system to learn.
4. **AWS managed services for production (Silas-led)** — durability, no DB wipes, production-grade backups + monitoring. Trade-off: AWS bill vs Railway free tier.
5. **Profile-toggled deployment shapes** — keep Railway/Confluent as fallback for local + demo backup so we're never locked to a single host.
6. **Anthropic Haiku over GPT-4-class models** — Dream AI's task is structured JSON over a bounded catalogue; Haiku is fast + cheap + safe-by-design.
7. **Vista (frontend) is a separate repo** — Haven is a pure API; contract is OpenAPI.
8. **Integrator-as-persona** — promoting Silas's frontend-integration concerns to a first-class persona caught gaps that customer-flow personas never could.

## Likely demo-day questions

**Q: "Walk me through the architecture."**
> Spring Boot monolith that owns all the business logic. Persists to AWS RDS (Postgres with pgvector for AI search), publishes events to AWS MSK via a transactional outbox, stores photos on Cloudflare R2, and calls Anthropic for Dream AI. Vista (Next.js) is a separate frontend; contract is OpenAPI. We support two profiles — `aws` for production and `railway` for local/demo fallback — so the same code runs in both shapes.

**Q: "Who built what?"**
> Two engineers. I drove the feature breadth — controllers, services, Dream AI, demo seeding, most of the test suite. Silas, our senior backend, led infrastructure — the AWS migration to RDS and MSK, deployment hardening, and senior review on cross-cutting decisions.

**Q: "Wait, Silas is also one of your personas — what's going on there?"**
> Same person, intentional. We created a persona for Silas because his lens — the frontend integrator wiring screens against the API — catches gaps the customer personas can't. Customer personas walk business flows; Silas walks the OpenAPI spec end-to-end and asks "can I actually render this?". His persona has 8 user stories captured the same way Temi or Amaka's are, and it caught real bugs — the validation-returning-401 issue, the editable-but-not-readable settings fields, the listing card needing 6 GETs to render. Treating his concerns as first-class made the API noticeably better.

**Q: "Why Spring Boot?"**
> (1) Bootcamp curriculum is JVM-focused; (2) Spring's batteries-included stance — security, JPA, Kafka, observability all in one — meant less wiring time, more feature time; (3) static type system caught a lot of bugs early in a 2-week sprint with no QA.

**Q: "Why a monolith?"**
> Naturally cohesive bounded context. Splitting it would have meant solving distributed-system problems the rubric doesn't ask for. Ships faster, fails predictably, can be split later.

**Q: "Why did you migrate from Railway to AWS?"**
> Railway's bundled-Postgres approach wiped the database on every redeploy — fine during dev, not OK for anything production-shaped. Silas led the migration to AWS RDS for durable storage and MSK for managed Kafka. We kept the Railway profile as a fallback for local development and demo backup.

**Q: "How do you switch between AWS and Railway?"**
> Spring profile. `--spring.profiles.active=aws` or set `HAVEN_PROFILE=aws`. Same application code, different `application-<profile>.yml`. Production runs `aws`; local dev or demo backup runs `railway`.

**Q: "What was the hardest part?"**
> Probably the inspection slot model — getting a DB-level guarantee that no two requests claim the same slot, without app-level locking. Solution: Postgres `EXCLUDE USING GIST` constraint on active requests only. Covered in session 4.

**Q: "What's the deploy story now?"**
> `git push origin main` → AWS picks up the build → [TBD pipeline details — confirm with Silas] → restarts the service. Health check at `/actuator/health`. Flyway migrations run on startup. The Railway fallback is still there if AWS has an outage during the demo.

**Q: "What would you change with another month?"**
> (1) Add an external moderation classifier on Dream AI; (2) split Dream AI ranking into a background job so users get true token-by-token streaming; (3) finish the OpenAPI per-endpoint annotation pass (planned in `docs/plans/`).

## Demo cue

If asked to "show the architecture", open three things side-by-side:
1. `docs/demo-prep/01-overview.md` (this file, the diagram)
2. `docs/TRADEOFFS.md` (the decision ledger)
3. The live Scalar UI at `haven.dreamhomes.today/scalar.html`

---

## What's pending (for tomorrow's Silas sync)

A few `[TBD]` markers in this doc + the actual profile-file scaffolding. Once you have these from Silas, I'll fill them in cleanly:

1. **Compute platform on AWS** — ECS Fargate? EKS? EC2 + ALB? (Just so the "deploy story" answer is accurate)
2. **RDS connection** — `DATABASE_URL` style or split `DB_HOST/PORT/USER/PASS`? Auth via IAM or password?
3. **MSK connection** — bootstrap endpoints + auth mechanism (IAM, SCRAM-SHA-512, mTLS?)
4. **Secrets management** — AWS Secrets Manager? Parameter Store? Env vars on the task definition?
5. **Profile naming** — happy with `aws` and `railway` as profile names, or prefer `prod` / `local`?

Once I have those, I'll:
- Create `application-aws.yml` and `application-railway.yml` with the right env-var bindings
- Set the default activation in `application.yml`
- Update this doc with concrete details
- Run `mvn verify` to make sure nothing breaks
