# DreamHomes Haven — State of the System

A one-document summary of what's shipped across phases 0–14 of the capstone, for the
review writeup. Pairs with `dreamhomes-prd.md` (the brief), `dreamhomes-userflows.md`
(the journeys), `TRADEOFFS.md` (every "we chose X over Y" decision), and the diagrams in
`docs/diagrams/`.

---

## At a glance

| | |
|---|---|
| **Stack** | Java 21 · Spring Boot 3.3.5 · Spring Security · Spring Data JPA · Hibernate 6 · Spring Kafka · JJWT 0.12.6 · Flyway · Lombok · Micrometer · springdoc-openapi · bucket4j · Testcontainers · `@EmbeddedKafka` |
| **Migrations** | Flyway V1 → V18 (18 numbered SQLs, schema-validated by Hibernate at startup) |
| **Tests** | **389 total** · **0 failures · 0 errors** at `mvn verify` |
| **Source files** | ~222 across 14 features in one Maven module (package-by-feature) |
| **Maven modules** | **1** (single-module Spring Boot app) |
| **Phases shipped** | 0 (foundation) → 14 (modular monolith experiment) → 15 (consolidation back to single module) |

## What got built (by phase)

| Phase | Theme | Migrations | Headline |
|---|---|---|---|
| 0 | Identity + JWT auth | V1–V2 | Stateless JWT with `tokenVersion` revocation; deny-by-default security |
| 1 | Properties + listings | V3 | Owner creates, publishes; public discovery |
| 2 | Inspections | V4 | Slots + requests; partial UQ on active slot claims |
| 3 | Offers | V5 | Submit + accept/decline; PENDING/ACCEPTED/DECLINED state machine |
| 4 | Notifications + Kafka | V6 | Outbox pattern, event-id dedup, sync vs async sources |
| 4.5–4.6 | Reliability | V7–V9 | `@Version` optimistic lock, GiST EXCLUDE on slot overlap, after-commit relay hook, DLQ, browse index |
| 5 | Verification + admin | V10–V11 | 4-track verification, admin moderation, `admin_audit_log`, suspend → tokenVersion bump |
| 6 | Public reads + comments | V12 | Verified badges on public reads, public profile, soft-delete comments |
| 7 | Agent assignment | V13 | Owner ↔ agent handshake (REQUESTED → ACCEPTED → REVOKED), partial UQ on pending + active |
| 8 | Observability | (none) | Actuator allowlist, Prometheus, OpenAPI, request-id correlation, JSON logs |
| 9 | Notification reads + engagement | V14 | `/mine`, unread count, mark-read; saves; lock-free `view_count` |
| 10 | Listing reviews | V15 | Post-deal reviews gated on CLOSED + ACCEPTED offer; profile aggregate |
| 11–13 | Photos + review takedown + counter-offers | V16–V18 | Server-assigned `display_order`; soft-delete reviews; `parent_offer_id` chain |
| 14 | Modular monolith experiment | (none) | Tried 33 Maven modules with `BannedDependencies` enforcement. Most `-api` modules were 3–9 files; one was empty. Build-time enforcement was solving a coordination problem that doesn't exist at single-author capstone scale. |
| 15 | Consolidation back to single module | (none) | Reverted Phase 14: 33 modules → 1, ~430 files relocated under `src/main/java`, 8 trivial `*Api` interfaces inlined back into direct service-to-service autowires (`NotificationApi` + `AdminAuditApi` kept where they earn their keep), `app-shared` cycle-breaker module deleted (no cycle to break anymore). Same 389 tests pass; same wire contract; better-proportioned structure. |

## The reliability story (the part most worth defending)

PRD §7's framing was "**a missed notification = a missed deal**." That single sentence
shaped the four most important architectural choices in the codebase:

1. **Transactional Outbox.** Inspection requests and offer submissions write the domain
   row + an `outbox` row in the same transaction. A scheduled relay polls the table
   with `FOR UPDATE SKIP LOCKED`, ships to Kafka, and stamps `published_at`. There is
   no `kafkaTemplate.send` anywhere in the application services — eliminating the
   dual-write race entirely.
2. **After-commit hook.** Services fire an `OutboxRowReadyEvent` from a
   `@TransactionalEventListener(AFTER_COMMIT)` so the relay drains immediately on the
   happy path. The 1-second poll stays as the safety net for the rare commit-then-crash
   window.
3. **At-least-once Kafka + consumer-side dedup.** Producer is `acks=all,
   enable.idempotence=true`. Consumers commit offsets manually (`MANUAL_IMMEDIATE`)
   only after the DB insert succeeds. `Notification.event_id` carries a `UNIQUE`
   constraint; the listener's `existsByEventId` check is the application-layer half.
   Net effect: effectively-once delivery without distributed-transaction ceremony.
4. **DLQ + bounded retry.** Spring Kafka's `DefaultErrorHandler` retries with
   exponential backoff (500 ms → 5 s, 30 s cap), then publishes to `<topic>.DLT` on the
   same partition. A wedged message can't head-of-line-block a partition forever.

**Two Kafka events, deliberately.** Per PRD §7, `INSPECTION_REQUESTED` and
`OFFER_SUBMITTED` are the only events that ride Kafka — they're the ones where missing
the message kills a deal. Verification decisions, listing approvals, comments, agent
handshake, reviews — all sync DB notifications. Rejecting the third sequence diagram's
proposed `LISTING_APPROVED` Kafka event in favour of the PRD's sync notification was a
deliberate design-fidelity call (see `TRADEOFFS.md` Phase 5 entry).

## The data-layer correctness story

Application-side checks are belt-and-suspenders; the DB is the actual guarantee. Every
correctness invariant we care about lives in a Flyway migration:

- **No two active inspection requests on the same slot** — partial UNIQUE
  `WHERE status IN ('PENDING','APPROVED')` (V4).
- **No overlapping inspection slots on the same listing** — GiST `EXCLUDE` constraint
  with `tstzrange` overlap operator (V8). PRD §6 explicitly forbids race conditions on
  inspection conflicts; this enforces it at the data layer.
- **No two ACCEPTED agents on the same listing** — partial UNIQUE
  `WHERE status='ACCEPTED'` (V13). Plus a parallel partial UQ on `REQUESTED` so owners
  can't spam invites.
- **No duplicate Kafka deliveries surviving** — `outbox.event_id UNIQUE` +
  `notifications.event_id UNIQUE` (V6).
- **No double review on the same deal** — `UNIQUE (listing_id, reviewer_user_id,
  reviewee_user_id)` (V15).
- **No half-deletes / half-decisions** — `CHECK` constraints pair `deleted_at` with
  `deleted_by_user_id` (V12, comments), `decided_at` with `decided_by_admin_id`
  (V10, verifications), and `decided_at` with terminal status (V13, agent_listings).
- **`@Version` optimistic locking** on the three aggregates with legitimate concurrent
  writers — `Listing`, `Offer`, `Verification`, `AgentListing` — mapped to 409 in
  `GlobalExceptionHandler`.

When a service catches a `DataIntegrityViolationException` to translate to a domain
exception, it uses `saveAndFlush` so the constraint fires inside the catch — not at TX
commit.

## The trust loop end-to-end

The capstone niche is "transparency, trust, and verified interactions." Here's how that
threads through the codebase:

1. **Identity** — every user authenticates via JWT; admins are seeded by Flyway
   (V11) and can't self-register.
2. **Verification** (Phase 5) — owners submit identity docs, properties submit
   ownership docs, agents submit credentials. Admin queue → approve → flips a badge
   timestamp on the appropriate row.
3. **Public discovery** (Phase 6) — anonymous browsers see listings + the verified
   badge stamps directly on the response. Cache headers mean the browse path is
   CDN-friendly.
4. **Engagement** (Phase 9) — authenticated users save, anonymous viewers bump the
   atomic `view_count`. Drives "popular" rankings without per-user view rows.
5. **Transaction** (Phases 2, 3, 7) — inspection request → offer → owner accepts.
   Optionally agents in between via the assignment handshake.
6. **Closure** (Phase 10) — once the listing flips to CLOSED with an ACCEPTED offer,
   both parties can post reviews. Average rating + count appear on every public
   profile.
7. **Moderation** (Phase 5, 6) — admin can take down listings or comments at any time;
   every action lands in `admin_audit_log`.
8. **Observability** (Phase 8) — every request carries a UUID correlation ID
   (`X-Request-ID` header + MDC); Prometheus scrapes Micrometer counters; OpenAPI
   spec is auto-generated from controller signatures.

## The code consistency story

Every feature follows the same shape so a reviewer can navigate by pattern:

- **Layered packages** — one package per feature (`auth`, `listing`, `offer`, etc.).
  Each has `Controller → Service → Repository → Entity` plus DTOs and exceptions.
- **`DomainException` hierarchy** — every domain exception declares its HTTP status;
  `GlobalExceptionHandler` maps them to RFC 7807 `ProblemDetail` without per-class
  boilerplate.
- **`@PreAuthorize` for role-based gates, service for state-based gates.** Role
  checks (e.g. "must be ADMIN") sit on controllers; multi-condition rules (e.g.
  "comment author OR listing owner OR admin") sit in services so future callers
  inherit them.
- **Lombok stack everywhere.** `@Getter @Setter @Builder @NoArgsConstructor
  @AllArgsConstructor` on entities. Services are `@Service @Slf4j @RequiredArgsConstructor`.
- **Tests describe behaviour we wrote, not the framework.** No tests for Spring
  routing, JPA derived methods, or jakarta validation. Every test name reads as a
  spec line — `successfulRegistrationReturns201WithUserSummary`, not `testRegister`.

## What's intentionally deferred

These are slots in the design ERD with explicit revisit triggers in `TRADEOFFS.md`:

- `ListingPhoto` — frontend cards render without photos for now.
- `ListingLike` — same shape as `ListingSave`; collapse if both ship.
- `MessageThread` + `Message` — the "in-app messaging" PRD §4.9 line.
- `Ad` — featured listings / featured agents.
- Counter-offer chain (`Offer.parent_offer_id`).
- Tiered admin roles.
- Agent-side reviews (need `AgentListing.id` stamped on `Offer` at acceptance).
- Review takedown / edit window.
- Listing lifecycle states beyond LIVE/PAUSED/CLOSED (e.g. CLOSED_RENTED vs CLOSED_SOLD).

Each is a deliberate "earn its keep" decision — building any of them now is straight-
forward (the data model leaves room) but they don't unlock new behaviour for capstone
demo scope.

## Test discipline

- **TDD-first** on every behavioural change. Every service method has a failing test
  before the implementation. Annotation changes, package moves, and pure refactors
  skip the cycle.
- **Test what we own.** Spring routing, Hibernate derived methods, jakarta validation,
  JJWT, BCrypt, Kafka — all have their own test suites. Our tests describe the
  behaviour we wrote.
- **Singleton Testcontainer Postgres + `@EmbeddedKafka`** in `AbstractPostgresIT`.
  Once-per-JVM startup amortised across every IT.
- **`@BeforeEach` AND `@AfterEach`** cleanup on non-transactional ITs in FK-ordered
  sequence. Prevents bleed between sibling test classes.
- **Descriptive test names** — every test name reads as an executable spec line.

## Diagrams

| File | What |
|---|---|
| `01-system-architecture.drawio` | Vista ↔ Haven ↔ Postgres/Kafka deployment view + per-phase service annotations |
| `02-erd.drawio` (rich) | Entity-relationship with cardinalities, constraints, partial indexes, ★ Phase markers |
| `02-erd.drawio` (simple) | High-level entity-name boxes for slides |
| `03-sequence-kafka-flows.drawio` | Per-event sequence diagrams |
| `04-class-diagram.drawio` | Full layered application view: controllers → services → repos → entities + DomainException tree + Outbox cluster |
| `05-entity-detail.drawio` | Domain entities only, with full attributes + methods + DB constraints |

## Project structure (after Phase 15 consolidation)

```
haven/
├── docs/
├── pom.xml                                              single Maven module
└── src/
    ├── main/
    │   ├── java/com/dreamhomes/haven/
    │   │   ├── DreamhomesHavenApplication.java
    │   │   ├── common/                                  cross-cutting infra:
    │   │   │   ├── DomainException, GlobalExceptionHandler, RequestIdFilter,
    │   │   │   ├── validation/, ratelimit/, web/, config/
    │   │   │   └── outbox/                              transactional outbox
    │   │   │                                              (OutboxEvent, OutboxRelay,
    │   │   │                                              OutboxMetrics, ...)
    │   │   ├── auth/                                    AuthService, JwtService,
    │   │   │                                              JwtAuthenticationFilter,
    │   │   │                                              SecurityConfig, MeController
    │   │   ├── user/                                    User, AgentProfile,
    │   │   │                                              UserProfileService,
    │   │   │                                              UserAdminService,
    │   │   │                                              UserCredentialsService
    │   │   ├── property/                                Property + service + controller
    │   │   ├── listing/                                 Listing + service + controller
    │   │   ├── photo/                                   ListingPhoto
    │   │   ├── engagement/                              ListingSave
    │   │   ├── agentlisting/                            owner ↔ agent handshake
    │   │   ├── comment/                                 Q&A on listings
    │   │   ├── inspection/                              slots + requests + events
    │   │   ├── offer/                                   offers + counter-offers + events
    │   │   ├── review/                                  ListingReview + ReviewAggregate
    │   │   ├── verification/                            Verification + admin decisions
    │   │   ├── notification/                            Notification + listeners +
    │   │   │                                              NotificationApi (kept)
    │   │   └── admin/                                   AdminAuditLog + facades +
    │   │                                                  AdminAuditApi (kept)
    │   └── resources/
    │       ├── application.yml
    │       ├── logback-spring.xml
    │       ├── db/migration/V1..V18.sql
    │       └── static/scalar.html
    └── test/
        └── java/com/dreamhomes/haven/
            ├── support/                                 AbstractPostgresIT, JwtTestSupport
            ├── auth/, user/, property/, ...             tests next to their code
            └── ...
```

### Cross-feature reads

Two `*Api` interfaces survive because they're genuinely cross-cutting:

- **`NotificationApi`** — many features write notifications via this seam.
- **`AdminAuditApi`** — many features write audit log entries on admin actions.

Everything else is a direct service-to-service autowire — `OfferService` injects
`ListingService`, `AdminUserService` injects `UserRepository`, and so on. There is
no build-time cross-feature enforcement; the discipline is package conventions +
code review, sized to a single-author codebase.

## How to read the codebase

If you're a reviewer with limited time:

1. **Start at `dreamhomes-prd.md`** — the brief.
2. **Open `02-erd.drawio` (rich)** alongside `05-entity-detail.drawio` — see what's in
   the data model, with constraints inline.
3. **Walk one feature end-to-end** — the agent-listing assignment is a good one because
   it covers all the discipline at once: Flyway migration, optimistic-lock entity,
   role-vs-state gates split between `@PreAuthorize` and the service, partial UQ at
   the data layer, sync notification flow, end-to-end IT.
4. **Read `TRADEOFFS.md`** — every architectural choice with a "why / cost / revisit"
   triplet. Forty-plus entries; every one has a real consequence.
5. **`mvn verify`** — 389 tests, 0 failures, ~3 minutes wall-clock.

---

## Tests layout (after Phase 15)

Tests live next to the code they exercise. There's only one source root, so the rule
is simple: every `*Test.java` and `*IT.java` lives at
`src/test/java/com/dreamhomes/haven/<feature>/`. Shared test fixtures
(`AbstractPostgresIT`, `JwtTestSupport`) live in `src/test/java/com/dreamhomes/haven/support/`.

| Test type | Lives at | Examples |
|---|---|---|
| Mockito unit tests | `src/test/java/com/dreamhomes/haven/<feature>/` | `ListingServiceCreateTest`, `OfferServiceSubmitTest`, `JwtServiceTest` |
| Common util unit tests | `src/test/java/com/dreamhomes/haven/common/` | `StrictEmailValidatorTest`, `RequestIdFilterTest`, `GlobalExceptionHandlerTest` |
| Controller `@WebMvcTest` slices | `src/test/java/com/dreamhomes/haven/<feature>/` | `AuthControllerLoginTest`, `OfferControllerTest`, `AdminListingControllerTest` |
| Repository ITs | `src/test/java/com/dreamhomes/haven/<feature>/` | `ListingRepositoryIT`, `OfferRepositoryIT`, `UserRepositoryIT` |
| Cross-feature flow ITs | `src/test/java/com/dreamhomes/haven/<feature>/` | `OfferFlowEndToEndIT`, `ReviewFlowEndToEndIT`, `AuthFlowEndToEndIT` |
| Common-infra ITs | `src/test/java/com/dreamhomes/haven/common/` | `PublicCacheHeadersIT`, `ObservabilityIT`, `AuthRateLimitIT`, `OutboxEventRepositoryIT` |

---

*Living document. Update when phases land or major architectural choices change. Last
updated after Phase 15 (consolidation back to single Maven module after the modular
monolith experiment in Phase 14 proved too heavy for capstone scale).*
