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
| **Source files** | ~222 across 14 features (each split into `-api` + `-impl` Maven modules) |
| **Maven modules** | **33** (parent + core + core-events + test-support + app-shared + 28 feature `api`/`impl` halves + integration-tests + app) |
| **Phases shipped** | 0 (foundation) → 14 (modular monolith restructure) |

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
| 14 | Modular monolith restructure | (none) | Single Maven module → 33 modules; per-feature `api/impl` split; `BannedDependencies` enforced; cross-aggregate reads through `*Api` interfaces only |

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

## Module topology (after Phase 14)

```
haven/
├── docs/
├── pom.xml                                            (parent reactor)
└── modules/
    ├── core/                                          DomainException, GlobalExceptionHandler, RequestIdFilter,
    │                                                  validators, AuthRateLimitFilter, KafkaErrorHandlerConfig,
    │                                                  Role enum, JwtPrincipal, WebConfig (cache-control + Page DTO),
    │                                                  springdoc starter — used by every module
    ├── core-events/                                   OutboxEvent + OutboxRelay — shared Kafka outbox infra
    ├── app-shared/                                    Pure-resources leaf: application.yml, Flyway V1..V18,
    │                                                  logback-spring.xml, static/scalar.html (zero deps to
    │                                                  break the test-support → impl → test-support cycle)
    ├── test-support/                                  HavenTestApplication, AbstractPostgresIT
    │                                                  (Testcontainers + EmbeddedKafka)
    ├── integration-tests/                             Cross-feature flow ITs + JwtTestSupport. Depends on every
    │                                                  -impl at scope=test; nothing depends on it. Houses
    │                                                  controller WebMvc tests that need cross-feature security.
    ├── app/                                           SpringBootApplication entry-point. Wires every -impl on
    │                                                  the runtime classpath; produces the executable fat jar.
    └── feature/
        ├── notification/api,impl                      NotificationApi (recordSync, recordAsync) +
        │                                              InspectionRequestedListener + OfferSubmittedListener
        ├── property/api,impl                          PropertyApi (5 methods)
        ├── listing/api,impl                           ListingApi (7 methods); uses PropertyApi
        ├── user/api,impl                              UserApi (3 methods); uses ReviewApi
        ├── review/api,impl                            ReviewApi (1 method); uses Listing/Offer/Notification/AdminAuditApi
        ├── auth/api,impl                              folds me/; SecurityConfig lives here
        ├── inspection/api,impl                        InspectionRequestedEvent in -api; uses ListingApi
        ├── offer/api,impl                             OfferSubmittedEvent in -api; OfferApi (1 method); uses ListingApi
        ├── comment/api,impl                           uses ListingApi + NotificationApi
        ├── agentlisting/api,impl                      uses ListingApi + UserApi + NotificationApi
        ├── photo/api,impl                             uses ListingApi
        ├── engagement/api,impl                        uses ListingApi
        ├── verification/api,impl                      uses UserApi + PropertyApi
        └── admin/api,impl                             AdminAuditApi for cross-feature audit log writes
```

### Allowed cross-feature edges

A `feature-X-impl` module can declare **`feature-Y-api`** as a dependency. It cannot
declare another feature's `-impl` — `mvn validate` rejects the build. **Zero
exceptions** as of the post-Phase-14 cleanup: the original auth-impl→user-impl and
admin-impl→user/verification-impl edges were retired by extracting three new admin /
credential APIs:

| API (in -api) | Owner impl | Replaces what direct edge |
|---|---|---|
| `UserCredentialsApi` | feature-user-impl | auth-impl reaching into `UserRepository` for login + register + tokenVersion bumps |
| `UserAdminApi` | feature-user-impl | admin-impl writing `User.suspendedAt` + `tokenVersion` and stamping identity / agent-credential badges |
| `VerificationAdminApi` | feature-verification-impl | admin-impl reading + writing `Verification.status` directly; the api now owns the badge-flip dispatch through `UserAdminApi` / `PropertyApi` |

The result: every `feature-impl` compiles against `*-api` only, including auth-impl
and admin-impl, and the `BannedDependencies` enforcer activates uniformly across all
14 feature-impl modules.

### `BannedDependencies` enforcer

The parent pom's `pluginManagement` declares the `maven-enforcer-plugin` rule that bans
direct deps on any `com.dreamhomes:haven-feature-*-impl` artifact. **All 14
feature-impl modules** activate the plugin (4-line declaration). `mvn -pl <module>
validate` fails fast on violations:

```
[ERROR] Rule 0: org.apache.maven.enforcer.rules.dependency.BannedDependencies failed:
[ERROR]   com.dreamhomes:haven-feature-user-impl:jar:0.0.1-SNAPSHOT <--- banned via the exclude/include list
```

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

## Tests layout (Phase 14 — completed)

Tests live with the code they exercise. The `legacy-features` module that held them
during the restructure has been deleted.

| Test type | Lives in | Examples |
|---|---|---|
| Pure unit tests (Mockito, no Spring) | `feature-*-impl/src/test/java` | `ListingServiceCreateTest`, `OfferServiceSubmitTest`, `JwtServiceTest` |
| Common util unit tests | `core/src/test/java` | `StrictEmailValidatorTest`, `RequestIdFilterTest`, `GlobalExceptionHandlerTest` |
| Single-feature controller `@WebMvcTest` (only auth-impl + admin-impl, since they have legal cross-feature deps) | `feature-auth-impl`, `feature-admin-impl` | `AuthControllerLoginTest`, `AdminListingServiceTest`, `JwtAuthenticationFilterTest` |
| Single-feature repository ITs | `integration-tests/src/test/java` | `ListingRepositoryIT`, `OfferRepositoryIT`, `UserRepositoryIT` |
| Controller `@WebMvcTest` that imports auth/user infra (most features) | `integration-tests/src/test/java` | `OfferControllerTest`, `NotificationControllerTest`, `AdminListingControllerTest` |
| Cross-feature flow ITs | `integration-tests/src/test/java` | `OfferFlowEndToEndIT`, `ReviewFlowEndToEndIT`, `AuthFlowEndToEndIT` |
| Common-infra ITs (need full Spring context) | `integration-tests/src/test/java` | `PublicCacheHeadersIT`, `ObservabilityIT`, `AuthRateLimitIT`, `OutboxEventRepositoryIT` |

The split rule: a test stays in its feature-impl module **only** if its imports are
satisfied by that module's legal compile classpath. The moment it reaches into another
feature's impl (via `SecurityConfig`, `JwtService`, `UserRepository`, etc.) it moves to
`integration-tests`, which legitimately depends on every `-impl` at scope=test. This is
exactly what a new feature would do today: write its `*ServiceTest` against `*Api` mocks
locally, write its `*FlowEndToEndIT` in `integration-tests` where the wiring is real.

---

*Living document. Update when phases land or major architectural choices change. Last
updated end of Phase 14 (modular monolith restructure).*
