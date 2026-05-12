# Trade-offs ledger

Every "we chose X over Y" decision worth remembering. Append as new ones land; revise when a defer becomes a do. **Format: choice → why → cost → revisit signal.**

Last updated after Phase 15 (consolidation back to single Maven module after the modular monolith experiment of Phase 14 proved heavier than the codebase warranted).

---

## Identity & data model

### `BIGSERIAL` IDs over `UUID`
- **Why**: smaller (8B vs 16B), faster index, easier debugging, native PG types throughout.
- **Cost**: enumerable IDs in URLs (an attacker can guess `/api/listings/2` exists from `/api/listings/1`); harder if we ever shard or merge databases.
- **Revisit when**: we need to expose IDs to untrusted parties or move to multi-region.

### Single `users` table with role enum (Option A → evolving to Option C)
- **Why**: one auth flow, one FK target everywhere, role changes are an `UPDATE`.
- **Cost**: role-specific data wants its own home — e.g. `agent_profiles` already split off.
- **Revisit when**: a role accumulates real domain data → split it into a `*_profiles` table (the Option C pattern). Already in motion: `agent_profiles` exists, `verifications` will be next.

### Single `address` text column on `Property`
- **Why**: trivial to ship, free-form input.
- **Cost**: can't filter listings by city/state in SQL — search is a `LIKE '%Lekki%'`. PRD location-based search becomes ugly.
- **Revisit when**: we add location search/filter (most likely Phase 6+).

### 3-state listing status (LIVE / PAUSED / CLOSED) vs design's 7
- **Why**: simpler state machine for MVP, fewer transition rules. Phase 5 confirmed PENDING is unnecessary on listings — listings go LIVE on creation per PRD §4.1; admin approval is a separate badge column (`approved_at`), not a status state.
- **Cost**: can't tell if a closed listing was rented or sold.
- **Revisit when**: analytics needs CLOSED_RENTED vs CLOSED_SOLD distinction.

### 3-state inspection request status vs design's 7
- **Why**: PENDING / APPROVED / DECLINED covers the MVP flow.
- **Cost**: no CONFIRMED / RESCHEDULED / COMPLETED / NO_SHOW / CANCELLED. Phase 5 territory.
- **Revisit when**: owner approve/decline + post-inspection notes ship.

### 3-state offer status vs design's 6
- **Why**: PENDING / ACCEPTED / DECLINED is enough for one-shot offers.
- **Cost**: no COUNTERED / WITHDRAWN / EXPIRED. Counter-offers are explicitly deferred.
- **Revisit when**: counter-offer flow ships (Phase 5+).

### Multiple PENDING offers per applicant on a listing
- **Why**: applicants should be able to up their bid; design says so.
- **Cost**: owner inbox can have noise from one bidder.
- **Revisit when**: PRD/owner UX feedback says "supersede prior offer" is wanted.

### Accepting one offer doesn't auto-decline the rest
- **Why**: keep the service simple; owner manually closes the listing when settled.
- **Cost**: owner has to clean up dangling PENDING offers.
- **Revisit when**: an owner dashboard surfaces this as friction.

### Listing.ownerId / Offer.ownerId denormalised from owning entities
- **Why**: ownership checks are fast (no join); written in lockstep at service layer.
- **Cost**: if a property's ownership ever transferred, listings would carry stale ownerId. Property transfer isn't in scope.
- **Revisit when**: property transfer ships → add a sync job or move to canonical lookup.

---

## Auth & security

### HS256 (HMAC) JWT signing over RS256 (RSA)
- **Why**: single-service deployment; symmetric key is simpler.
- **Cost**: can't share verifier with an external service without sharing the secret.
- **Revisit when**: a second service needs to verify tokens.

### Token revocation via `token_version` column + DB lookup per request
- **Why**: simple, no Redis dependency; logout invalidates all tokens for the user.
- **Cost**: 1 DB query per authenticated request. Not bottlenecked at our scale; would need caching at higher load.
- **Revisit when**: load testing shows it as a hot path; add a `tokenVersion` cache with short TTL.

### `BCrypt` cost factor 10 (default)
- **Why**: ~80 ms per hash, fine for production at our scale.
- **Cost**: login latency floor is ~80 ms (DUMMY_HASH path adds the same cost for missing users).
- **Revisit when**: high login-per-second load measured in benchmarks.

### Email enumeration via `409` status code on duplicate registration
- **Why**: response body no longer leaks the email; the status code itself is the residual signal.
- **Cost**: an attacker can still tell registered vs unregistered emails by hammering /register.
- **Revisit when**: we build email verification → switch to `202 Accepted` for both new and existing emails.

### Password deny-list is small (~20 entries)
- **Why**: catches the most obvious choices without a giant resource file.
- **Cost**: doesn't approach haveibeenpwned-grade coverage.
- **Revisit when**: capstone scoring rewards or production usage warrants it; either embed a 100k-entry list or HIBP API.

### Rate limiting via in-memory bucket4j
- **Why**: zero infrastructure for capstone scope.
- **Cost**: doesn't survive multi-instance deployment — each pod has its own counter.
- **Revisit when**: we run more than one app instance behind a load balancer; switch to Redis-backed buckets.

### `DUMMY_HASH` from `BCryptPasswordEncoder.encode("never-matches")` at class load
- **Why**: real BCrypt hash guaranteed to flow through the constant-time match path.
- **Cost**: ~80 ms one-time cost at app start.
- **Revisit**: never; this is defensive correctness.

---

## Reliability & messaging (Phase 4.5–4.6)

### Transactional outbox pattern
- **Why**: PRD §7 — "missed notification = missed deal." Inline `kafkaTemplate.send` in services would lose events if Kafka is down post-DB-commit.
- **Cost**: extra table, extra Spring scheduler, extra `OutboxEvent` entity per published event.
- **Revisit**: never; this is the foundation. Don't backslide into inline publish.

### Outbox poll cadence 1 s + after-commit hook
- **Why**: the after-commit hook keeps happy-path latency to tens of ms; the poll catches crashes between commit and listener invocation.
- **Cost**: 1 query/second/instance even when idle.
- **Revisit when**: outbox queue depth alerts say poll cadence isn't keeping up; or move to PG `LISTEN/NOTIFY` to drop the poll entirely.

### `event_id` UUID dedup at notification consumer
- **Why**: Kafka is at-least-once; same event can arrive twice on rebalance/retry.
- **Cost**: extra UUID column + uniqueness constraint + existsByEventId check per event.
- **Revisit**: never; this is the consumer-side half of effectively-once delivery.

### Manual offset commit (`enable-auto-commit=false`, `MANUAL_IMMEDIATE`)
- **Why**: don't ack until DB insert succeeds, so crashes between consume and write redeliver instead of dropping the message.
- **Cost**: a slow `record(...)` blocks the partition. Our path is ~10 ms (an indexed `existsByEventId` + insert), so it's fine.
- **Revisit when**: notification persistence ever blocks on something slow (network call, expensive computation).

### `acks=all` + `enable.idempotence=true` on the producer
- **Why**: every event we ship matters per PRD §7.
- **Cost**: producer latency rises slightly (waits for all in-sync replicas).
- **Revisit**: never for these two events. If a future, low-stakes event ships, that one can opt-out per topic.

### DLT cap at 30 s (`maxElapsedTime` on the backoff)
- **Why**: don't head-of-line-block a partition forever on a stuck message.
- **Cost**: a transient outage longer than 30 s sends real messages to DLT — they need manual replay.
- **Revisit when**: we have production data on transient outage durations; tune up if 30 s is too aggressive, down if it's too patient.

### Kafka partition key = `listingId` (not `slotId` / `offerId`)
- **Why**: per-listing event ordering on the consumer — matches the system architecture diagram.
- **Cost**: events for different slots/offers on the same listing serialise on one partition, slightly less parallelism per listing.
- **Revisit**: never; this is the design's stated promise.

### DLT auto-replay not built
- **Why**: arbitrary replay is dangerous (could re-trigger downstream side effects).
- **Cost**: ops has to inspect and replay manually.
- **Revisit when**: we build an admin console; DLT inspection + selective replay is a natural Admin tool.

### Embedded Kafka in tests (vs testcontainers Kafka)
- **Why**: faster startup, in-JVM, no Docker requirement for the broker side.
- **Cost**: not the same broker version as production; some Kafka 4.x features (KRaft-only behaviours) won't surface here.
- **Revisit when**: we use a Kafka feature that diverges between embedded and prod.

---

## Concurrency & data integrity

### `@Version` optimistic locking on `Listing` and `Offer`
- **Why**: design ERD requires it; concurrent owner/admin/automation writes silently lost without it.
- **Cost**: extra `version BIGINT` column; clients see 409 on concurrent edits.
- **Revisit**: never (design fidelity).

### GiST `EXCLUDE` on `inspection_slots(listing_id, tstzrange(...))`
- **Why**: PRD §6 — data-layer prevention of overlapping slots. Service-level checks would race.
- **Cost**: `btree_gist` extension required; constraint cost on insert (small).
- **Revisit**: never (PRD-load-bearing).

### Partial UNIQUE on `inspection_requests(slot_id) WHERE status IN (PENDING, APPROVED)`
- **Why**: one active claim per slot, declined requests free the slot.
- **Cost**: more nuanced than a plain UNIQUE — devs need to read the comment to understand.
- **Revisit**: never.

### `saveAndFlush` (not `save`) in slot creation
- **Why**: forces the EXCLUDE constraint check inside our `try { } catch (DataIntegrityViolationException)` instead of at TX commit (where it's untranslatable).
- **Cost**: tiny — one extra round-trip vs lazy flush.
- **Revisit**: never for any service that needs to translate constraint failures to domain exceptions.

---

## Testing

### `@AfterEach` cleanup on every non-transactional IT
- **Why**: ITs that drive HTTP commit rows that bleed into sibling test classes; per-class cleanup at start AND end keeps the schema empty.
- **Cost**: every new flow IT has to remember the FK-ordered wipe sequence.
- **Revisit when**: this becomes load-bearing; lift to a `@TestExecutionListener` that wipes once per class automatically.

### Singleton testcontainer Postgres + embedded Kafka in `AbstractPostgresIT`
- **Why**: ~once-per-JVM startup cost amortised across all ITs.
- **Cost**: ITs share a database; cross-test pollution requires the cleanup discipline above.
- **Revisit when**: we need per-test isolation for some specific test (override locally).

### `@Component`-scanned `JwtTestSupport` in test sources
- **Why**: Spring discovers it via component scan during test runs; tests inject and use the helper.
- **Cost**: slightly non-idiomatic vs `@TestConfiguration` + explicit `@Import`.
- **Revisit when**: we want a finer scope; mostly never.

### No tests for framework behaviour (Spring/Kafka/Hibernate/jakarta validation)
- **Why**: the framework already tests itself; our tests should describe behaviour we wrote.
- **Cost**: zero — this is an asset, not a debt.
- **Revisit**: never.

---

## API surface & response shape

### `Page` JSON via `VIA_DTO` (not direct `PageImpl`)
- **Why**: Spring Boot 3.3 explicitly warns about `PageImpl` shape stability; pin the wire format.
- **Cost**: slightly different JSON keys (no impact at this scale).
- **Revisit**: never.

### Embedded `PropertySummary` in `ListingResponse`
- **Why**: frontend renders a card from one GET, not N+1.
- **Cost**: extra bulk fetch in `browsePublic` (1 listings + 1 properties query, not N+1).
- **Revisit when**: listings exceed ~10k and we want a JOIN-based query for true single-roundtrip.

### Public discovery `Cache-Control: public, max-age=60, stale-while-revalidate=300`
- **Why**: PRD §6 says public discovery must be fast and cacheable.
- **Cost**: public listing changes take up to 60 s to propagate through CDN/browser caches.
- **Revisit when**: real-time freshness becomes a product requirement.

---

## Verification & admin (Phase 5)

### `LISTING_APPROVED` is a sync DB notification, NOT a Kafka event
- **Why**: PRD §7 explicitly says only two Kafka events (`INSPECTION_REQUESTED`, `OFFER_SUBMITTED`); listing approvals + verification updates are sync DB notifications. The third sequence diagram (`docs/diagrams/03c-listing-approved.drawio`) shows a Kafka flow but the PRD wins on conflicts (priority #1: design fidelity, PRD is source of truth).
- **Cost**: notification delivery is bound to the admin's transaction; no async fan-out. If a future product wants email/SMS dispatch on approval, that'd be a follow-on async layer keying off the new notification row.
- **Revisit when**: the third Kafka event becomes worth the cost — i.e. cross-service consumers exist for listing/verification events.

### Listings are LIVE on creation; admin "approval" is a verified-listing badge
- **Why**: PRD §4.1 — *"Listings are live immediately with an unverified badge — verification is non-blocking."* The userflows §5 line about "approve before they go live" is reconciled by treating approval as the badge stamp, not a visibility gate.
- **Cost**: a fraudulent listing is publicly visible from creation until an admin takes it down. We mitigate with the takedown action + rate-limited public browse, not pre-publish gating.
- **Revisit when**: product demands pre-publish admin approval (e.g. for a high-trust premium tier).

### Single `ADMIN` role; tiered admin permissions deferred
- **Why**: PRD §4.10 mentions "tiered admin permissions" but the capstone has one admin tier of work — verification + listing/user moderation. Tiers add a permissions matrix without a current consumer.
- **Cost**: every admin can do every admin action. Audit log captures who did what, but not who *should have* been allowed.
- **Revisit when**: a second admin tier ships (e.g. read-only auditor, or content-moderator-only).

### `verifications.document_refs` is JSONB metadata only — no raw files in DB
- **Why**: PRD §6 — *"All sensitive document references stored as metadata only."* Storage of actual ID images/PDFs is an out-of-scope concern.
- **Cost**: an external file storage system has to land before this is end-to-end useful in production. For capstone demos, the metadata pointer is the artifact.
- **Revisit when**: file storage integration ships (S3/GCS/etc.) → admin queue UI shows real documents.

### Re-submission after rejection creates a NEW row (rejected one preserved as audit history)
- **Why**: keeps the decision trail intact. If we updated the existing row in place, the rejection reason and timestamp would be lost.
- **Cost**: `verifications` grows over time per user; queue queries already filter by `status = 'PENDING'`, so the dead rows are partial-index-skipped.
- **Revisit when**: forensic queries get expensive — at that point, archive resolved rows older than N days.

### Optimistic lock (`@Version`) on `Verification`
- **Why**: two admins racing to decide the same row resolves to one winner with a clean 409.
- **Cost**: extra `version BIGINT` column.
- **Revisit**: never (matches the @Version pattern on Listing/Offer).

### Self-moderation blocked at the service layer (`CannotModerateSelfException`)
- **Why**: prevents a single admin from locking themselves out by suspending their own account.
- **Cost**: an admin who genuinely needs to lock their account out has to ask another admin (acceptable; this is moderation theatre done right).
- **Revisit**: never.

### Suspension bumps `tokenVersion`, reactivation does not
- **Why**: bumping on suspend invalidates outstanding JWTs immediately; bumping on reactivate is wasted churn (the user must log in fresh anyway, and the suspend bump already invalidated whatever they had).
- **Cost**: subtle — easy to mis-symmetrise on a future read of this code.
- **Revisit**: never; well-commented in `AdminUserService.reactivate`.

### Admin verification decision rejection requires a reason; approval allows a null reason
- **Why**: rejected submissions need actionable feedback for the user. Approvals don't carry the same UX requirement.
- **Cost**: API surface area asymmetric (reject body required, approve body unused).
- **Revisit when**: product wants admin notes on approvals too — extend approve to take a body.

### Seeded admin via Flyway placeholders + env-driven password hash
- **Why**: PRD §4.10 — "Seeded admin account, no self-registration." Idempotent `ON CONFLICT DO NOTHING` keeps the migration safe across environments. Hash comes from env, not source.
- **Cost**: missing env vars in prod fall back to the dev default — a hash for `ChangeMeNow!`. Prod ops must rotate immediately on first deploy.
- **Revisit when**: secrets management ships (Vault/KMS) — pull the hash from the secret store at startup instead.

### Per-decision Micrometer counters via `AdminMetrics`
- **Why**: lets ops chart approval/rejection volume by verification type, plus listing actions and user moderations.
- **Cost**: bounded cardinality (8 verification labels + 2 listing actions + 2 user actions), but every admin action is one extra map lookup + counter increment.
- **Revisit when**: a counter explodes in cardinality (won't happen with the current label set).

---

## Public reads & comments (Phase 6)

### Verification badge timestamps surfaced as `null` (not omitted) on public reads
- **Why**: keeps the JSON shape stable for the frontend's verified-badge rendering — every listing/profile response always has the badge fields present, just sometimes null.
- **Cost**: a tiny number of extra bytes per response.
- **Revisit when**: payload size becomes a real concern (probably never at this scale).

### `/api/users/{id}/profile` is the only public projection of a user
- **Why**: minimal blast radius — explicit `PublicUserProfile` record (no email, phone, passwordHash, tokenVersion). The compile-time guarantee is that there's no path from User → public response without going through this projection.
- **Cost**: any new public field needs an explicit add.
- **Revisit**: never; this is the privacy boundary.

### `AgentProfile` lookup for credential badge is conditional on `role = AGENT`
- **Why**: the typical hit (owner / applicant profile) costs one query, not two. The agent-only branch loads the profile lazily.
- **Cost**: a malformed agent (role=AGENT but no AgentProfile row) returns null `agentCredentialVerifiedAt` instead of failing loudly. Acceptable for a public read.
- **Revisit when**: registration ever lets a role=AGENT user persist without an AgentProfile (shouldn't happen — AuthService.register creates both atomically).

### Suspended user still has a public profile (with `suspended: true` flag)
- **Why**: an owner with active listings shouldn't disappear from the listing's owner card just because they were suspended; the frontend renders a muted state from the flag.
- **Cost**: a suspended bad actor still appears on the profile route. The takedown of their listings is the actual customer-facing remedy.
- **Revisit when**: product decides suspended profiles should 404. Trivial flip.

### Comments use **soft-delete** (`deleted_at`, `deleted_by_user_id`, `deletion_reason`)
- **Why**: keeps the audit trail intact. An admin or owner takedown is reversible; the row stays for forensics + appeals; the partial index `comments_active_per_listing_idx` keeps public reads index-only by filtering on `deleted_at IS NULL`.
- **Cost**: comments table grows monotonically; rotation/archival is a future concern.
- **Revisit when**: comment volume hits a few hundred thousand rows AND we want shorter index footprints — partition by listing or archive deleted-older-than-N-days.

### `comments_delete_complete` CHECK constraint pairs deleted_at + deleted_by_user_id
- **Why**: prevents half-deletes (one column set, the other null) at the data layer. Service-level enforcement is belt-and-suspenders.
- **Cost**: zero. Schema-level enforcement is a free win.
- **Revisit**: never.

### Comment delete authorisation lives in the service, not the controller
- **Why**: rule is "author OR listing owner OR admin" — three orthogonal conditions. Putting it in `@PreAuthorize` would either need a custom SpEL or split across multiple endpoints. The service is also where future callers (admin moderation tools, batch ops) inherit the rule.
- **Cost**: controller can't pre-reject before hitting the service; one extra DB lookup on a 403 path.
- **Revisit**: never; this is the right separation.

### Self-comments by listing owner don't fire a notification
- **Why**: owners aren't surprised by their own posts. Saves a notification row + a UI noise event.
- **Cost**: a multi-account owner posting from a different account would still notify themselves — that's correct, the suppression is purely "same userId as owner".
- **Revisit**: never.

### Comments don't ride Kafka
- **Why**: PRD §7 — only INSPECTION_REQUESTED and OFFER_SUBMITTED are Kafka events. Listing approvals, verification updates, comments — all sync DB notifications.
- **Cost**: no async fan-out for comment notifications. If a future product wants email-on-new-comment, that'd be a follow-on async layer keying off the new notification row.
- **Revisit when**: cross-service consumers exist for comment events.

### Comment body capped at 4000 chars at both DB and validation layers
- **Why**: keeps Postgres rows reasonable; matches the @Size cap in the request DTO.
- **Cost**: zero in practice — long comments are usually low quality anyway.
- **Revisit**: never.

### `DELETE /api/comments/{id}` accepts an optional reason body via `@RequestBody(required = false)`
- **Why**: authors deleting their own comment can fire-and-forget (no body needed); admins / owners are encouraged to supply a reason for the audit trail.
- **Cost**: API surface area — DELETE with a body is unusual but not wrong (RFC 9110 says it's permitted; just ambiguous).
- **Revisit when**: an HTTP client library refuses to send DELETE bodies — at that point, switch to a query string `?reason=...`.

---

## Agent-listing assignment handshake (Phase 7)

### Verified-agent badge is a discovery signal, NOT an assignment gate
- **Why**: PRD §4.1 — verification is *non-blocking but rewarded*. Owners filter for verified agents at search time via the public profile endpoint; the assignment endpoint only enforces `role = AGENT`. Matches the listing-badge pattern (visible signals, not visibility gates).
- **Cost**: an owner can technically invite an unverified agent. The verified badge is the trust signal that makes that visible.
- **Revisit when**: product wants assignment to require credential verification — trivial flip in `AgentListingService.request`.

### Both parties + admins can revoke `ACCEPTED` (or pending) assignments
- **Why**: owners need to switch agents; agents need to be able to resign. A separate "agent quit" path would just be a second method doing the same thing.
- **Cost**: an admin could revoke as part of moderation but no separate audit log row is written here (the `decisionReason` carries the why); if/when that becomes important we'd extend the moderation audit machinery to cover assignments too.
- **Revisit when**: legal/compliance asks for an immutable admin-revoke trail.

### One outstanding `REQUESTED` invite per listing (partial UQ enforces it)
- **Why**: simpler model, prevents owners spamming invites. Owner workflow is "revoke pending → invite new agent".
- **Cost**: fewer parallel options for the owner; can't fan-out invites and accept the first.
- **Revisit when**: owners ask for "shop around" workflow — at that point relax the partial UQ + add a separate "first-accept-wins" race resolver.

### One `ACCEPTED` row per listing (second partial UQ)
- **Why**: matches the design — single active agent per listing. Prevents two ACCEPTED rows even if a `revoke` and an `accept` race.
- **Cost**: zero. Schema-level invariant.
- **Revisit when**: territory-split (per-region agents per listing) becomes a product requirement.

### `requested_by_owner_id` is denormalised from listings.owner_id at request time
- **Why**: "my outstanding invites" filter is fast — no join. Service writes both columns transactionally so they can't diverge at insert.
- **Cost**: if a listing's owner ever changes (not in scope), the assignment's `requested_by_owner_id` becomes stale. Same pattern as `Listing.ownerId` denormalised from `Property.ownerId`.
- **Revisit when**: ownership transfer ships — add a sync job or move to canonical lookup.

### `@Version` optimistic lock on `AgentListing`
- **Why**: the natural race here is owner-revoke vs agent-accept on the same row, both reading status=REQUESTED. Optimistic lock resolves to one winner with a clean 409.
- **Cost**: extra `version BIGINT` column.
- **Revisit**: never (consistent with Listing/Offer/Verification).

### `decline` requires a reason; `accept` allows null reason
- **Why**: declined invites need actionable feedback for owners ("why didn't they take it?"); accepts don't.
- **Cost**: API surface area asymmetry.
- **Revisit when**: product wants accept-side notes — extend the request DTO.

### `revoke` lives on `AgentListingService`, not split into `cancelInvite` + `endAssignment`
- **Why**: the operation is the same — caller authorisation, status transition, sync notification to the OTHER party. A pre-decision row goes REQUESTED → REVOKED; an active row goes ACCEPTED → REVOKED. One method handles both.
- **Cost**: callers can't tell from the method name which case they're in. The status check inside the method makes the intent explicit.
- **Revisit when**: the two cases need genuinely different side effects.

### Notification recipient = "the other party"
- **Why**: `request` notifies the agent; `accept`/`decline` notify the owner; `revoke` notifies whichever party didn't initiate. Symmetric and easy to remember.
- **Cost**: zero — the alternative (notify everyone) would be noisy.
- **Revisit**: never.

---

## Observability + production polish (Phase 8)

### `/actuator/health` is **public**, `/actuator/prometheus` is **auth-gated**
- **Why**: load balancers + k8s liveness/readiness probes need to hit health without a token; metric scraping is an operator concern, not public.
- **Cost**: anonymous probes can determine the app is up — that's the point of a probe. The trade is that no anonymous caller learns the DB or Kafka health (component details gated by `show-details: when-authorized`).
- **Revisit when**: production puts the actuator port on a separate network interface — at that point `/actuator/prometheus` can drop the auth gate because the surface is network-isolated.

### Actuator exposure list is allowlist-only (`health,info,prometheus`)
- **Why**: every other endpoint (env, beans, mappings, threaddump, heapdump) is sensitive in prod. Default Boot exposes only `health` + `info` over HTTP, but adding actuator typically tempts wider exposure. We pin the allowlist explicitly.
- **Cost**: anyone debugging must use a profile-specific override or talk to ops.
- **Revisit**: never; this is a security baseline.

### `springdoc-openapi` over hand-written OpenAPI YAML
- **Why**: derives the spec from controller signatures + jakarta-validation annotations, so it can't drift from the actual implementation. Frontend (vista) consumes `/v3/api-docs` directly; demos use Swagger UI at `/swagger-ui.html`.
- **Cost**: another dependency (~3 MB classpath). Generation runs at app startup — adds ~200 ms.
- **Revisit when**: we adopt API-first design and want the spec to drive code generation — at that point flip to a static YAML and use generators.

### `RequestIdFilter` runs at `Ordered.HIGHEST_PRECEDENCE`
- **Why**: the request id must be in MDC before security, controllers, exception handlers, and any logger fires. If anything logs before this filter, the line is unattributed.
- **Cost**: zero; ordering is free.
- **Revisit**: never.

### Inbound `X-Request-ID` header is honoured (not overwritten)
- **Why**: vista can pre-tag a user-facing action — bug reports quote a single id that traces from browser → frontend → backend log lines → metrics. Distributed tracing in poor man's mode.
- **Cost**: a malicious caller could supply colliding ids to confuse log searches. Mitigated by also stamping a server-side timestamp on every line; no security guarantee made about request-id uniqueness.
- **Revisit when**: we move to OpenTelemetry / W3C trace-context — at that point map this onto `traceparent` instead of a custom header.

### Logback profile split: dev = human pattern, prod = JSON
- **Why**: local logs need to be readable. Prod logs need to be machine-ingestible.
- **Cost**: two paths to keep in sync; the request-id field appears in both via `%X{requestId}` (pattern) and `includeMdcKeyName` (JSON).
- **Revisit when**: prod logging stops being JSON — won't happen.

### Empty MDC requestId is rendered as `-` (not blank)
- **Why**: `%X{requestId:--}` syntax keeps the column-aligned dev pattern stable for log lines outside a request (scheduled tasks, Kafka listeners, app startup).
- **Cost**: zero — purely cosmetic for grep.
- **Revisit**: never.

### Test infra: `RequestIdFilter` works without Spring (unit tests use `MockHttpServletRequest`)
- **Why**: filter only depends on `MDC` + servlet API, no autowiring. We can spec it in pure JUnit + Mockito at near-zero cost.
- **Cost**: zero.
- **Revisit**: never.

---

## Notification reads + engagement (Phase 9)

### `markRead` is idempotent — preserves the original "first read at" timestamp
- **Why**: a second mark-read call on an already-read row is a no-op rather than overwriting `readAt`. Keeps the first-read timestamp meaningful for analytics.
- **Cost**: zero.
- **Revisit**: never.

### `unread-count` is its own endpoint, not a header on `/mine`
- **Why**: the badge in the dashboard nav refreshes more often than the inbox itself. A scalar GET is cheap (`COUNT(*)` with the partial composite index from V6 is index-only) and doesn't pull a list.
- **Cost**: an extra HTTP roundtrip if the client wants both. Acceptable; HTTP/2 multiplexes them anyway.
- **Revisit when**: the dashboard genuinely needs both at once on first paint — at that point bundle into the `/mine` response or use a websocket push.

### Notification list is scoped server-side by `recipient_user_id` (no caller-supplied filter)
- **Why**: every list query is filtered by the JWT principal's id. There's no `?recipientId=…` knob — the privacy boundary is in code, not the client's hands.
- **Cost**: zero. This is the right separation.
- **Revisit**: never.

### `view_count` increment is a **lock-free atomic SQL UPDATE** that bypasses `@Version`
- **Why**: a popular listing's view counter shouldn't churn the optimistic lock or contend with owner edits. `UPDATE listing SET view_count = view_count + 1 WHERE id = ?` is the simplest correct thing — no first-level cache, no row lock, no version bump.
- **Cost**: the response body carries the pre-increment count (we read first, then increment). Acceptable: the bumped count appears on the next read, and the wire is consistent within a single request.
- **Revisit when**: the counter becomes a hot row (write-storm on a viral listing) — at that point batch increments through Redis or a background flusher.

### `view_count` is aggregate-only (no per-user view rows)
- **Why**: per-anonymous-visitor row would explode storage and FK volume. The aggregate counter is enough for "most viewed" rankings without leaking who viewed what.
- **Cost**: can't compute "users who viewed this listing" or "viewing-but-not-saved" funnels.
- **Revisit when**: product wants the funnel — at that point add a separate (sampled?) `listing_view_events` table.

### Saves use a **composite primary key** `(user_id, listing_id)`, not a surrogate id
- **Why**: the natural key IS the row's identity. Composite PK gives us free uniqueness, makes "did this user save this listing?" an index-only seek, and the service's `existsByUserIdAndListingId` short-circuit becomes a no-roundtrip on the cached PK.
- **Cost**: composite IDs need an `@IdClass` + serialisable wrapper class. Tiny.
- **Revisit**: never.

### Save and unsave are **idempotent** (re-save / re-unsave returns 200 No Content)
- **Why**: makes the frontend's "toggle saved" UI race-free. Concurrent double-clicks can't accidentally 409.
- **Cost**: the wire response doesn't tell the caller "you already had this saved" — they're meant not to care.
- **Revisit when**: a UX needs the distinction.

### Save endpoints use `POST` + `DELETE` on the parent listing, not a separate resource
- **Why**: the relationship IS a sub-resource of the listing — `POST /api/listings/{id}/save` reads naturally as "save this listing." `DELETE` mirrors. The toggle is two distinct operations, not one PATCH.
- **Cost**: zero.
- **Revisit**: never.

---

## Listing reviews (Phase 10)

### Reviews are **immutable** for Phase 10 — no edit, no soft-delete
- **Why**: keeps the trust signal honest ("this is what they said at the time"). Edit and admin-takedown both add design decisions (window-of-correction? takedown audit?) that don't earn their keep at MVP.
- **Cost**: a typo lives forever; an inflammatory review can't be moderated until we ship the takedown path.
- **Revisit when**: a real abuse case shows up — at that point ship admin soft-delete first (mirror of `comments.deleted_at` + `admin_audit_log` row), and a 24-hour author edit window second.

### Participants are **owner ↔ applicant only** — agents can't be reviewed yet
- **Why**: PRD §4.10 talks about agent ratings on profiles, but the review participants list maps cleanly onto the existing offer-acceptance signal. Agent reviews need their own "deal completed via this agent" event, which we don't model yet (the AgentListing→Offer relationship isn't formalised).
- **Cost**: agent profiles show `averageRating: null` even after they help close deals. Acceptable visual.
- **Revisit when**: AgentListing.id gets stamped onto Offer at acceptance time — that's the natural join point.

### A review's "deal" is identified by **listing + ACCEPTED offer**, not a Deal entity
- **Why**: lean cut. Our existing `Offer.status = ACCEPTED` is the canonical "the deal happened" signal. Adding a Deal entity would be a ceremony pass with no new behaviour.
- **Cost**: re-using a listing for a second deal (sale fell through, re-listed, sold to someone else) would let the old reviewer review the same counterparty twice — wait, no, the UQ on `(listing, reviewer, reviewee)` blocks that. The second buyer is a different reviewer, so they get their own row. This actually works.
- **Revisit when**: we model lifecycle states richer than CLOSED (CLOSED_RENTED + CLOSED_SOLD).

### `UNIQUE (listing_id, reviewer_user_id, reviewee_user_id)` — one review per pair per listing
- **Why**: prevents review-bombing the same person on the same deal. Composite UQ at the data layer; service short-circuits with a 409 before hitting it.
- **Cost**: a reviewer can't update their review without the edit endpoint we deferred.
- **Revisit when**: edit ships.

### Rating is `SMALLINT` 1..5, NOT a free-form decimal
- **Why**: small fixed scale matches every consumer-facing review system — easier to render as stars, average is meaningful, no interpretation drift. CHECK at DB layer + `@Min/@Max` at validation layer.
- **Cost**: zero. 5-star is the convention.
- **Revisit**: never.

### `ReviewAggregate` is a single JPQL projection (`AVG` + `COUNT` in one query)
- **Why**: the public profile endpoint shouldn't fan out to two GETs every time someone visits. One projection query, one DB roundtrip.
- **Cost**: SQL `AVG` returns null on empty set — service coerces to `ReviewAggregate.empty()` so callers don't have to special-case.
- **Revisit when**: the average-rating becomes a hot-enough computation to want denormalising onto `users.cached_avg_rating` — at that point add a trigger or scheduled refresher.

### Self-review check fires **before** any DB lookup
- **Why**: cheapest possible 403 path. Reviewer == reviewee is impossible by definition of "review someone"; surfacing that as `InvalidRevieweeException` keeps the response shape consistent with other counterparty-mismatch failures.
- **Cost**: zero.
- **Revisit**: never.

### Reviews are **publicly readable** — no JWT required to GET a listing's or user's reviews
- **Why**: trust signals only work if anonymous browsers can see them. The frontend renders them on listing detail + agent profile pages. Cache headers are wired through `WebConfig` so CDNs can serve them.
- **Cost**: zero — reviews are inherently public content (the reviewer chose to publish them).
- **Revisit**: never.

### `reviewer_user_id <> reviewee_user_id` CHECK constraint
- **Why**: defence-in-depth for the self-review case. Service blocks it; the data layer enforces it; if a future code path forgot the check, the DB rejects the row.
- **Cost**: zero.
- **Revisit**: never.

---

## Photos + review takedown + counter-offers (Phases 11–13)

### `listing_photos.url` is a **pointer**, never raw bytes (PRD §6)
- **Why**: PRD §6 forbids raw file storage in the application DB. URL points at external object storage (CDN, S3, etc.).
- **Cost**: object-storage layer itself is out of capstone scope (PRD §9); for the demo, vista posts a CDN-hosted URL directly. A real prod deployment needs the upload pipeline.
- **Revisit when**: object-storage integration lands; at that point add presigned-URL minting endpoints.

### `display_order` is **server-assigned (max+1)** on insert, no UQ
- **Why**: client-supplied `display_order` would collide on simultaneous uploads or wrong client logic. Auto-incrementing max+1 is correct under typical use; a UQ would force the client to handle 23505 retries for no benefit.
- **Cost**: two simultaneous uploads on the same listing could compute the same `next` and end up with a tie. Tie-broken by id. Not a correctness issue.
- **Revisit when**: drag-and-drop reorder ships — at that point add a transactional bulk-resequence endpoint that takes the full ordered list of photo IDs.

### Photo lifecycle is **hard-delete**, not soft-delete (unlike comments + reviews)
- **Why**: photo metadata has no reputational dimension. The image at the URL might already be gone (CDN expiry); keeping a soft-deleted row carries no value.
- **Cost**: zero.
- **Revisit**: never.

### Review takedown is **soft-delete** mirroring `Comment` (V12 pattern)
- **Why**: trust-signal data needs a forensic trail. Author self-cancel + admin moderation both go through the same row mutation; partial indexes (V17) keep public reads at index-only seek.
- **Cost**: review row grows monotonically; rotate when volume warrants.
- **Revisit when**: review volume > a few hundred thousand AND we want shorter index footprints.

### Reviewee **cannot self-takedown** their own bad review
- **Why**: letting the recipient of a bad rating soft-delete it would defeat the entire trust signal. Only the author (who chose to publish) or an admin (moderation) can take it down.
- **Cost**: zero. This is the correct invariant.
- **Revisit**: never.

### Admin takedown writes `admin_audit_log`; author self-delete does NOT
- **Why**: moderation actions need an immutable trail (PRD §4.10). Author self-cancel is just a personal correction — `deleted_by_user_id` already records who.
- **Cost**: two slightly-different code paths in `ReviewService.delete`. Acceptable; the rule is exactly two cases.
- **Revisit**: never.

### Soft-delete excludes the row from `aggregateForUser` immediately
- **Why**: the average rating + count update the moment a takedown lands. Otherwise admins would have to wait for a cache refresh or manual recompute.
- **Cost**: zero — the JPQL query just adds `AND r.deletedAt IS NULL`.
- **Revisit**: never.

### Counter-offers are a **chain**, not in-place edits
- **Why**: history matters for trust + dispute resolution. "Owner countered ₦5M, applicant counter-countered ₦4.8M, owner accepted" reads as four immutable rows in the chain — the truth is unambiguous. In-place editing would lose intermediate states.
- **Cost**: schema gets a self-FK (`parent_offer_id`); a deep chain costs N rows. Acceptable; chains are short in practice.
- **Revisit**: never.

### `proposedByUserId` is the **single source of authorisation truth** for counters
- **Why**: every row records who proposed it. The "other party" can act — accept, decline, or counter. Owner-vs-applicant role check would be wrong for counters because both sides can propose.
- **Cost**: one extra column, denormalised from "applicant_id on original / inferred from chain depth elsewhere." Worth the simplicity at the service layer.
- **Revisit**: never.

### `COUNTERED` is **terminal-but-tracked** (not deletable, not transitionable)
- **Why**: parent rows in the chain need to stay readable for history. Once countered, the parent's status is frozen; the chain continues on the child.
- **Cost**: zero.
- **Revisit**: never.

### Counter-offers fire a **sync notification**, not Kafka
- **Why**: PRD §7 keeps Kafka strictly to two events. The original `OFFER_SUBMITTED` rides Kafka; counter-offers stay sync because both parties are already negotiating actively (and the path is a simpler one-DB-write event).
- **Cost**: no async fan-out for counter-offer notifications. If we ever need email-on-counter, it's a follow-on async layer keying off the new notification row.
- **Revisit when**: cross-service consumers exist for counter-offer events.

### `respond` now allows applicants too (was OWNER-only pre-Phase 13)
- **Why**: applicants need to accept owner counters. The `@PreAuthorize` was relaxed to `hasAnyRole('OWNER', 'APPLICANT')`; the service does the proposer check.
- **Cost**: a controller-level role gate is less precise than the service-level one. Not a problem here — applicants and owners are the only roles that should hit this path; admins moderate listings, agents handle assignments separately.
- **Revisit**: never.

### Counter does NOT auto-decline parent — parent goes COUNTERED, not DECLINED
- **Why**: COUNTERED carries the "this got countered" signal in the audit trail. DECLINED would conflate "someone walked away" with "we're still negotiating."
- **Cost**: another status value. The ENUM already had room for it.
- **Revisit**: never.

---

## Phase 15 — Consolidation back to a single module

### Reverted Phase 14: 33 Maven modules → 1, package-by-feature kept
- **Why**: Phase 14 split the codebase into per-feature `-api`/`-impl` Maven module pairs (33 modules total) with a `BannedDependencies` enforcer to prevent cross-feature impl reaches. After it shipped, a senior dev pointed out the obvious: this is a single-author capstone, not a 50-engineer codebase. Comparing to `origin/main` (silas's parallel implementation of the same PRD) confirmed it — silas shipped 9 features in 1 module, 1 pom, 119 files, no enforcer. Our `-api` modules averaged 9 files, one was empty (`engagement/api`), and the discipline the build enforced was a coordination problem we don't have. So Phase 14 got unwound: ~430 Java files moved back to a single `src/main/java` tree, 8 of the 10 `*Api` interfaces inlined back into normal service-to-service autowires, and the `BannedDependencies` plugin retired. **Same 389 tests pass, same wire contract, same database schema, ~80 lines of pom collapsed to one ~200-line single-module pom.** All entries below this one document Phase 14 as historical context — the *decisions* still capture genuine learnings (the `*Api` boundary discipline, the cross-aggregate ID-only reads), they just no longer manifest as Maven modules.
- **Cost**: ~2 days of mechanical work. 8 `*Api` interfaces deleted (`ListingApi`, `PropertyApi`, `UserApi`, `OfferApi`, `ReviewApi`, `UserCredentialsApi`, `UserAdminApi`, `VerificationAdminApi`). The post-Phase-14 cleanup that retired the auth-impl→user-impl + admin-impl→user/verification-impl exceptions also unwinds — `AuthService` autowires `UserRepository` again, `AdminUserService` autowires `UserRepository` again. The `app-shared` cycle-breaker module dies (no cycle to break). `HavenTestApplication` dies (no longer needed; the production `DreamhomesHavenApplication` is the only `@SpringBootConfiguration` on the test classpath). The *narrative* "we have 33 modules with enforced boundaries" is replaced with "we tried it, measured the cost, and consolidated when the discipline wasn't pulling its weight."
- **Revisit when**: another contributor joins the codebase and we need build-time enforcement of cross-feature boundaries. Or when the codebase grows past ~50k LOC and the package conventions stop catching boundary leaks.

### Two `*Api` interfaces preserved: `NotificationApi` + `AdminAuditApi`
- **Why**: not all of Phase 14's `*Api` work was waste. Two interfaces survive because they're genuinely cross-cutting:
  - **`NotificationApi`** — many features write notifications (inspection requests, offer submissions, agent assignments, verification decisions, admin moderation, comments, listing reviews). The seam earns its keep: producers don't depend on the notification entity, just the contract.
  - **`AdminAuditApi`** — many features need to record an audit log entry on admin actions. Same reasoning.
- **Cost**: two interfaces. Negligible.
- **Revisit**: never.

### Folder layout: package-by-feature under `src/main/java/com/dreamhomes/haven/<feature>/`
- **Why**: matches silas's structure (the parallel implementation of the same PRD), matches the codebase as it was before Phase 14, and is the natural shape for a Spring Boot capstone. Each feature is one folder; cross-feature wiring happens through normal `@Service` autowires.
- **Cost**: package-private discipline is enforced by code review, not by the build. With one author, that's fine.
- **Revisit**: never (at this scale).

---

## Modular monolith restructure (Phase 14: P1–P5) — *historical context, reverted in Phase 15*

The entries below document the design decisions of the modular monolith experiment.
The decisions themselves still capture real learnings (cross-aggregate reads via
projections, ID-only foreign keys, the `*Api` boundary discipline). They just no
longer manifest as Maven modules — the codebase consolidated back in Phase 15.

### Maven multi-module with `feature/<name>/<api|impl>` per-feature pairs
- **Why**: build-level enforcement of public/private boundaries. Each feature exports a thin `-api` (interfaces + DTOs + enums + exceptions) and an opaque `-impl` (entity + repo + service + controller). Cross-feature consumers compile against `-api` only — they physically cannot import another feature's entity, repo, or service impl. The legacy single-module layout had the same package-by-feature shape but no enforced isolation; a contributor could `import com.dreamhomes.haven.user.UserRepository` from anywhere.
- **Cost**: 33 modules vs 1. ~30 poms to maintain. Each `*Response.from(Entity)` factory dropped (DTO-in-api can't see entity-in-impl) — construction inlined in controllers / services. Slightly longer `mvn` reactor build: ~30s cold vs ~15s before.
- **Revisit**: never going back. The principle is sound and the build is fast enough.

### Folder layout: `modules/feature/<name>/<api|impl>` (nested) over flat `feature-<name>-<api|impl>`
- **Why**: visual grouping. Each feature is one folder; `cd feature/listing/` shows api + impl side by side. The `modules/` wrapper keeps the repo root clean (5 entries: `docs/`, `pom.xml`, `LICENSE`, `README.md`, `modules/`).
- **Cost**: artifactId still `haven-feature-listing-api` (kept descriptive for `mvn -pl :artifact` invocations and error messages); the artifactId-vs-folder mismatch is a known minor friction. Same pattern Spring Boot itself ships (`spring-boot-starter-web` artifact lives under `spring-boot-project/spring-boot-starters/spring-boot-starter-web/`).
- **Revisit**: never.

### Explicit `<relativePath>../../pom.xml</relativePath>` over self-closing `<relativePath/>`
- **Why**: self-closing form makes Maven resolve the parent through the local repo only. Mid-edit changes to the parent's `<dependencyManagement>` don't propagate until `mvn install -N` runs first, which interrupts incremental work. Explicit on-disk paths read the parent fresh from the filesystem every build.
- **Cost**: visual noise (nested feature poms have `../../../../pom.xml`). Acceptable — `<relativePath>` is read once per pom, never on the hot path.
- **Revisit**: never.

### Cross-aggregate reads through `*Api` interfaces, never repositories
- **Why**: the user's principle — "payment imports account.service, never account.repository." After P3, every cross-feature read goes through one of `ListingApi`, `PropertyApi`, `UserApi`, `OfferApi`, `ReviewApi`, `NotificationApi`, `AdminAuditApi`. Repositories are package-internal to their `-impl` modules.
- **Cost**: 7 interface modules to design + maintain. Each new cross-feature read needs an Api method; can't just reach into the other repo. That's the discipline tax — and it's the point.
- **Revisit**: never.

### `Role` enum stays in `core` (not `feature/user/api`)
- **Why**: Role is a security primitive used in JWT claims, `@PreAuthorize` annotations across every controller, and `JwtPrincipal`. Putting it in `feature/user/api` would force `core` (which holds JwtPrincipal) to depend on user-api, polluting the dependency graph for a transitive enum. Same reasoning applies to `JwtPrincipal` — it lives in `core` even though its package is `com.dreamhomes.haven.auth`.
- **Cost**: split-package — `core` and `feature/user/impl` both contribute classes to `com.dreamhomes.haven.user`. Maven handles split packages fine; IDE may warn cosmetically.
- **Revisit**: never.

### Auth + admin impl-impl exceptions retired via three narrow admin/credential APIs
- **Why**: the original Phase 14 plan documented two "intra-aggregate" exceptions where auth-impl reached directly into user-impl, and admin-impl reached into user-impl + verification-impl. The "shared bounded context" framing was theology smoothing over a real modeling miss — the `BannedDependencies` rule existed to prevent exactly this kind of cross-impl reach. So the exceptions were collapsed by extracting three new APIs: `UserCredentialsApi` (login + register + tokenVersion writes for auth), `UserAdminApi` (suspend + reactivate + badge stamps for admin and verification), and `VerificationAdminApi` (decision write + badge-flip dispatch). All three live in their owning feature's `-api` module; the impls return small projection records (`UserCredentials`, `UserAdminView`, `VerificationAdminView`, `RegisteredUser`) so consumers never touch a JPA entity. Net result: every feature-impl compiles against `*-api` only, the enforcer activates uniformly across all 14 impls, and admin-impl + auth-impl pom descriptions no longer carry "exception" caveats.
- **Cost**: three new interfaces (~80 lines), three new projection records, ~25 lines of mapping in the impls; auth-impl's register flow lost its direct AgentProfile creation (now bundled inside `UserCredentialsApi.create`) — that's a meaningful behavioural move worth knowing about. The admin response DTOs (`AdminUserResponse`, `AdminVerificationResponse`) moved out of admin-api into their owning feature-api as `UserAdminView` / `VerificationAdminView`; the wire shape stays identical, the OpenAPI schema name changes.
- **Revisit**: never. The split rule is now uniform — no feature-impl ever sees another feature-impl's entity, repository, or service.

### `BannedDependencies` enforcer over ArchUnit tests
- **Why**: build-time (validate phase) enforcement is faster + closer to the violation than a test-time assertion. A future contributor who tries to add `feature-X-impl` as a dep to another feature's impl gets a clear message at `mvn validate`, before any code compiles. ArchUnit would only fire during the test phase and adds a test dependency to every module.
- **Cost**: per-module `<plugin>` activation (4 lines × 12 modules). Two modules (auth-impl, admin-impl) opt out via simply not declaring the plugin.
- **Revisit**: never. Optionally add ArchUnit later as a complementary in-test check.

### `ReviewAggregate` + `ReviewApi.aggregateForUser` split early into `feature/review/api`
- **Why**: `UserProfileService` (in feature/user/impl) embeds the review average + count on every public profile. Without `ReviewApi`, user-impl would have to depend on review-impl, which would block user splitting in P3c (review hadn't been split yet). The thin api-only slice broke the chicken-and-egg: review-api shipped before review-impl.
- **Cost**: review feature was split across two phases (api in P3c, impl in P3k) instead of one.
- **Revisit**: never.

### `legacy-features` retired; tests live with the code they test
- **Why**: `legacy-features` was a transitional catchall during P1–P3 that held everything not yet split (resources, `HavenTestApplication`, every test). With every feature now split, the module had no reason to exist — and "legacy" is a smell, since a new feature would never ship its tests there. So the migration that the original Phase 14 plan deferred actually got done: every test moved to where it belongs (see the "Tests layout" table in STATE-OF-THE-SYSTEM.md), `app-shared` was created as a pure-resources leaf to break the cycle, `HavenTestApplication` moved to `test-support`, and `legacy-features` was deleted from the reactor. All tests pass in their new homes (389 total at the time of this writing — count grows as new features land).
- **Cost**: ~80 test files relocated; one test-support pom hadn't been re-installed in `~/.m2` so a stale POM masked a missing JDBC driver dep until reinstalled. The `BannedDependencies` rule pushed many controller `@WebMvcTest` files into `integration-tests` (they all import `SecurityConfig` + `JwtService`, which they can't legally see from a feature-impl whose only auth touchpoint is `*Api`). Fine — it captures the real architectural fact that those tests aren't single-feature.
- **Revisit**: never. The split rule is now mechanical: a test stays in its feature-impl iff its imports satisfy the feature-impl's legal compile classpath. New features write `*ServiceTest` against `*Api` mocks locally and `*FlowEndToEndIT` in `integration-tests`.

### `app-shared` as a zero-dep resources module
- **Why**: `test-support` (which provides `AbstractPostgresIT`) needs to bring `application.yml`, Flyway migrations, `logback-spring.xml`, and `static/scalar.html` onto the classpath of every IT. Originally those resources lived in `app`, but `test-support` can't depend on `app` (cycle: app → every -impl → test-support). The fix is to extract the resources into a leaf module (`app-shared`) with **zero dependencies** that both `app` and `test-support` consume — it's the bottom of the DAG.
- **Cost**: extra module that only ships an empty jar with resources. Worth it — the cycle was real and `app-shared` is the smallest module that breaks it.
- **Revisit**: never.

### `WebConfig` lives in `core`, `SecurityConfig` in `feature/auth/impl`
- **Why**: `WebConfig` (Spring Data Web `Page` mode + the `PublicCacheHeadersInterceptor` registration) is cross-cutting and needed by every Spring Boot context (production + every IT that boots `HavenTestApplication`). It was briefly in `app` but ITs in `integration-tests` boot `HavenTestApplication` from `test-support`, which doesn't see `app` — so `WebConfig` had to go down to `core` (which already hosts the interceptor itself). `SecurityConfig` stays in `feature/auth/impl` because it's the auth feature's wiring; nothing else needs to see it.
- **Cost**: `core` now depends on `spring-data-commons` (for `@EnableSpringDataWebSupport`) and `springdoc-openapi-starter-webmvc-ui` (so `/v3/api-docs` is exposed by every web boot, prod and test alike). Both are tiny starters that auto-detect on classpath.
- **Revisit**: if any module needs Spring web *without* `Page` DTO serialization or cache headers (none today).

### User-facing persona pages + hosted error reference are deferred
- **Why**: today the persona docs ([`docs/users/`](users/)) and per-error explainers ([`docs/errors/`](errors/)) live as markdown in the repo. Scalar API descriptions reference the error pages via the configurable `haven.errors.type-base` URI; there are no public marketing pages for the personas. Hosting these on a real `dreamhomes.com` site is a follow-on once the backend is past capstone review — premature now.
- **Cost**: the API's `ProblemDetail.type` URI today resolves to a GitHub markdown file rather than a polished docs page. Self-explanatory but not pretty. Persona references in API descriptions were stripped (the docs in `/docs/users/` are the source of truth, not the OpenAPI surface).
- **Revisit when**: a hosted docs site exists. Override `HAVEN_ERRORS_TYPE_BASE` to point at the new origin (e.g. `https://docs.dreamhomes.com/errors/`); leave the markdown files in `docs/errors/` as the source the hosted pages render from. For personas, build `dreamhomes.com/personas` as a marketing surface and link from API docs / Scalar's "Description" markdown if useful.

### Move sites' DTO factories (`Response.from(Entity)`, `Request.toCommand()`) deleted, construction inlined in -impl
- **Why**: factories in -api couldn't see -impl entities. Inlining the construction in controllers / services keeps DTOs in -api as pure data shapes.
- **Cost**: 12+ inlined `toResponse(Entity)` static helpers in controllers (one per impl module). Boilerplate but explicit.
- **Revisit**: when MapStruct or similar gets adopted; right now manual construction is fine.

---

## Phase 16 — Post-audit improvements (gaps surfaced by silas's branch + the Kafka audit)

### `NewTopic` beans pin partition count + replication factor
- **Why**: partition count is the throughput knob you can't change later without operational pain (re-partitioning preserves neither order nor downstream consumer offsets). Auto-create at first publish would inherit broker defaults silently. `KafkaTopicConfig` declares one `NewTopic` bean per produced topic plus a sibling `.DLT` so spring-kafka's admin creates them with the shape we want on broker connect.
- **Cost**: another two beans per topic, two new properties (`haven.kafka.topic-partitions`, `haven.kafka.replication-factor`). Trivial.
- **Revisit**: when scaling the cluster — bump `replication-factor` to match the broker count.

### `PhotoStorage` interface + `R2PhotoStorage` / `LocalPhotoStorage`
- **Why**: needed real image hosting, not the URL-passing placeholder we shipped earlier. Cloudflare R2 is S3-compatible so the AWS SDK v2 client works against it with a custom endpoint — no Cloudflare-specific SDK lock-in. The pluggable interface lets dev + tests run with `LocalPhotoStorage` (synthesises a placeholder URL, no bytes persisted) so we don't need R2 credentials to exercise the upload pipeline. Production overrides `haven.photos.storage=r2` and supplies the credentials.
- **Cost**: one new dependency (`software.amazon.awssdk:s3`), one wire-contract change on `POST /api/listings/{id}/photos` (now multipart instead of JSON), one new package `photo/storage/`. The endpoint change broke the existing IT — fixed by switching to `MockMultipartFile` + `multipart()` request builder.
- **Revisit**: when we want CDN in front (Cloudflare Workers / R2 custom domain) — the URL the storage returns becomes the CDN-fronted URL, no app change needed.

### JPA auditing as belt-and-suspenders for entity timestamps
- **Why**: every entity used to set `createdAt = Instant.now()` either in its default initializer or in the service before save. Easy to forget on a new path. `@EnableJpaAuditing` + `@CreatedDate` / `@LastModifiedDate` makes the auditor populate them on persist/update if the field is null — a free safety net. The existing manual `Instant.now()` calls stay (so behaviour is unchanged) and auditing only fires when someone forgets.
- **Cost**: one new config bean + one annotation per entity (12 entities) + import bookkeeping. Harmless if nobody ever forgets to set the timestamp manually; meaningful when somebody does.
- **Revisit**: never. Could push further to delete the manual `Instant.now()` calls and rely only on auditing — defer until a future cleanup since the current shape is strictly safer.

### `spring-boot-devtools` for local hot reload
- **Why**: silas had it, we didn't. Iteration speed during local dev is real productivity. Marked `optional` so it never reaches the production classpath (Spring Boot's auto-config disables it in jars launched via `java -jar`).
- **Cost**: one pom dependency, scope=runtime+optional. Zero impact in prod.
- **Revisit**: never.

### Admin analytics endpoint with real (not hardcoded) aggregates
- **Why**: ops needs a one-shot platform-health view. Six counts: total users, suspended users, open listings, closed listings, pending verifications, pending offers. Each is a single index-backed query — endpoint runs O(1) regardless of table size.
- **Cost**: one new controller, service, DTO + four `countBy...` repository methods. ~80 lines of source + 200 of tests.
- **Revisit**: when the dashboard is polled aggressively or the field count grows (>10 fields) — back this with a Micrometer metric pipeline or a materialised view rather than per-request `count(*)`.

---

## Phase 16.5 — Trade-offs ledger cleanup (post-audit Tier 1/2/3 sprint)

This block resolves the 13 entries flagged as "lazy coding or ops polish" by the
honest re-audit of all 126 prior TRADEOFFS entries. Items not listed here
remain as-is; they were classified as legitimate scoped trade-offs.

### JWT signing: HS256 → RS256 (`haven.jwt.private-key` + `haven.jwt.public-key`)
- **Why**: HMAC means every party that verifies a token also has the secret to mint one. RS256 splits that — the private key signs, the public key verifies. Future fan-out (mobile, vista, internal services) can hold only the public half.
- **Cost**: env vars are now PEM-encoded multiline strings instead of a 32-byte hex secret. Constructor checks the keys are RSA, ≥ 2048 bits, and that the modulus matches between private + public. README documents the `openssl genpkey` workflow.
- **Revisit**: never. If we move to JWKS-served public keys for vista, the verify side gets simpler still.

### `POST /auth/register` returns 202 Accepted in every branch (anti-enumeration)
- **Why**: the previous 201/409 split let an attacker probe whether an email was registered just by hitting the endpoint. Always-202-with-empty-body removes that signal entirely. Service still inserts the user for fresh emails; duplicates and TOCTOU collisions are silently swallowed (logged for ops).
- **Cost**: caller no longer learns server-issued user id from the register response — they call `POST /auth/login` next, which returns a JWT (with the userId embedded). One wire-contract change. Drop two now-dead types: `UserResponse` DTO + `EmailAlreadyRegisteredException`.
- **Revisit**: never. If async email verification is ever added, the 202 already implies "we're processing it" — the contract's already aligned.

### Seeded-admin env vars fail loud on missing
- **Why**: `application.yml` previously shipped a bcrypt hash of "ChangeMeNow!" as the default admin password. A deploy that forgot to override `ADMIN_PASSWORD_HASH` would silently ship that. Removed both defaults — Spring property resolution now refuses to start if either is unset, mirroring how `HAVEN_JWT_PRIVATE_KEY` works.
- **Cost**: every IT had to register `ADMIN_EMAIL` + `ADMIN_PASSWORD_HASH` via `@DynamicPropertySource` (done in `AbstractPostgresIT`); local dev needs an `htpasswd -nbBC 10 "" "..." | tail -c +2` one-liner (documented in README).
- **Revisit**: never.

### `POST /api/listings/{id}/report` (Ngozi backlog item)
- **Why**: any authenticated user can flag a listing for moderation. Single insert into `listing_reports` (with a `(listing_id, reporter_user_id)` unique constraint enforcing one-report-per-user) plus a `LISTING_REPORTED` notification fanned out to every admin so the moderation queue surfaces fresh reports without polling.
- **Cost**: new `listingreport` feature package (model + repo + dto + service + controller + exception), V20 Flyway migration, one new `NotificationKind`. Admin read endpoint (paginated queue + filter by reason) is intentionally NOT in this PR — separate ticket.
- **Revisit**: when the read side ships, the `OFF_PLATFORM_FEES` payload is the place to add aggregate-by-reason analytics for ops.

### Manual `Instant.now()` removed from services; JPA auditing is the only path
- **Why**: every service previously stamped `createdAt` / `updatedAt` by hand alongside the `.builder()`. JPA auditing already populated the same fields on persist/update — two sources of truth, inconsistent in practice (some services forgot `setUpdatedAt` on PATCH paths). Drop the manual calls; trust the auditor.
- **Cost**: a handful of service unit tests had assertions like `assertThat(...getCreatedAt()).isNotNull()` that broke once the service no longer set the field — those moved either to "the IT verifies the persist path" or to a Mockito stub that mimics auditing. Rewrites the prior "JPA auditing as belt-and-suspenders" entry: it's now the single mechanism, not a safety net.
- **Revisit**: never.

### `DatabaseCleanupTestExecutionListener` replaces 21 per-IT `@AfterEach` cleanup blocks
- **Why**: every IT used to redeclare a private `clean()` method with FK-ordered `deleteAll()` calls. 21 files, all subtly different, easy to forget when adding a new table. Centralised into a single `TRUNCATE … RESTART IDENTITY CASCADE` statement run by a `TestExecutionListener` registered on `AbstractPostgresIT`.
- **Cost**: ~232 lines of test code deleted; adding a new table now requires updating the listener's `TRUNCATE_SQL` constant. Listener is registered FIRST in the `@TestExecutionListeners` list so its `afterTestMethod` runs LAST — after Spring's transactional rollback releases the connection.
- **Revisit**: never.

### Auto-decline sibling `PENDING` offers when one accepts
- **Why**: previously, accepting one offer left every other PENDING offer on the listing in PENDING — they'd sit forever, the owner saw stale rows in the queue, and the losing applicant never learned the deal closed without them. Now `OfferService.respond` flips siblings to `DECLINED` in the same transaction and fires `OFFER_AUTO_DECLINED` notifications.
- **Cost**: one new `OfferRepository` method, one new `NotificationKind`, ~40 lines of service. Decline path is unchanged (no fan-out).
- **Revisit**: when async email/SMS notifications join the system, the auto-decline notifications might want a different priority tag than first-class user actions.

### MapStruct adoption for entity → DTO mapping
- **Why**: 12 hand-rolled `static toResponse(Entity)` helpers across controllers + services. Each is a positional record constructor — adding a field to the DTO means hand-editing every callsite (which is exactly what the `displayName` change last sprint demanded). MapStruct generates the implementation at compile time, infers the field-by-field copy from same-named accessors, and surfaces missing or ambiguous mappings as compile errors.
- **Cost**: one new dependency + annotation processor (`org.mapstruct:mapstruct` + `mapstruct-processor` + `lombok-mapstruct-binding`). 11 new `@Mapper(componentModel = "spring")` interfaces. `ListingMapper` had to spell out per-field `@Mapping(source = "listing.x")` because both arguments expose `id`. Service unit tests construct mappers via `new XxxMapperImpl()` (the generated impl); `@WebMvcTest` controller slices `@Import` the impl class so the bean is in the slice context.
- **Revisit**: never. Rewrites the prior "static mappers — fine for now" entry from Phase 15.

### `OutboxRelay` publishes async (no `.get()`); `kafka.publish.duration` Timer
- **Why**: the previous `kafkaTemplate.send(...).get()` blocked the relay thread on broker latency — a slow ISR sync would hold open whatever transaction the after-commit hook ran in. Now the publish is fire-and-callback via `whenComplete`. The callback runs on the producer I/O thread and stamps `publishedAt` in a fresh transaction (via `TransactionTemplate`) because the originating tx is already closed. `Timer.Sample` wraps each attempt and registers under `haven.kafka.publish.duration` tagged by `topic` + `outcome=success|failure`.
- **Cost**: relay constructor grew two args (`TransactionTemplate`, `MeterRegistry`). Publish failures and post-publish save failures both log + retry on the next scheduled poll — the consumer-side `event_id` dedup keeps that idempotent. Existing `OutboxRelayTest` updated to assert the timer fires with the right tags; existing listener ITs already used Awaitility, so the async timing change didn't break them.
- **Revisit**: when the producer thread pool gets tuned for throughput, we'd want a dedicated executor for the markPublished callback rather than running it on the Kafka I/O thread.

### Listener concurrency = `${haven.kafka.topic-partitions:3}`
- **Why**: default `concurrency = 1` left N-1 partitions queueing behind a single consumer thread. Setting it equal to the topic's partition count gives one consumer thread per partition — full parallel drain at the cost of N threads in the consumer container.
- **Cost**: spelled out as the property reference rather than a literal so the listener's parallelism stays in sync with `KafkaTopicConfig`'s partition count if either is changed.
- **Revisit**: when partition count grows past available CPU, decouple consumer thread count from partition count by introducing a separate property.

### `haven.outbox.dlt` depth gauge (mirror of `haven.outbox.unpublished`)
- **Why**: ops can already alert on `haven.outbox.unpublished > 0` — that's "outbox row stuck before Kafka". The new `haven.outbox.dlt` gauge covers the post-DLT-route side: "consumer-side processing failed long enough to exhaust retries". One Gauge per DLT topic, tagged with `topic=`, sourced via Kafka `AdminClient` end-offset query. `sum(haven_outbox_dlt)` gives platform-wide DLT depth; `haven_outbox_dlt{topic="..."}` drills in.
- **Cost**: new component + one Kafka AdminClient call per scrape per topic. AdminClient timeout (5s) bounded; failures emit `-1` so dashboards distinguish "not measured" from "0 depth". Scrape adds a few hundred ms when broker is healthy.
- **Revisit**: when the AdminClient overhead matters (i.e. scrape interval drops below 5s), cache the last value and refresh on a separate schedule.

## Phase 17 — Account-settings surface (merged in from PR #6, hardened in `checklist/v1`)

### `PATCH /api/me` rewrites email directly (no verify-the-new-address flow)
- **Why**: the platform has no email-delivery infrastructure yet. A proper change-email flow needs: generate single-use token → mail it to the *new* address → user clicks → swap on the user row. Until that pipeline exists, gating email change behind it would block the Settings page entirely. The temporary shortcut is "any authenticated caller can rewrite their own email synchronously".
- **Cost**: an attacker who briefly holds a victim's JWT can swap the address of record before initiating a password reset to *their* email — classic account-takeover pivot. Mitigated by bumping `tokenVersion` on email change (so the leaked JWT dies on its next request, before the attacker can use it to chain to password reset), but the legitimate user has no defence if the attacker controls both the swap and the password-reset trigger in the same minute.
- **Revisit**: as soon as email delivery lands. Move email change to a two-step flow (`POST /api/me/email-change-requests` → token → confirm), keep `PATCH /api/me` for everything else.

### `userId` (not `id`) is canonical inside the `/api/me/*` family
- **Why**: Dayo's persona audit flagged the mixed `id` / `userId` field naming as a real frontend papercut. PR #6 originally used `id` on `MyAccountProfile`; we renamed it to `PrivateUserProfile.userId` so every response under `/api/me` (`MeResponse`, `PrivateUserProfile`) uses `userId`. Admin writes still return `id` — that's a separate concern outside this family.
- **Cost**: temporary asymmetry between the `/me` family (`userId`) and admin/public projections (`id`). Two names for one concept, but they're consistently grouped now.
- **Revisit**: a v2 contract pass to unify the full surface on one name. Likely `id` because it's the broader-used token across REST APIs — but it's a frontend-breaking rename, so explicit deprecation cycle required.

### `/error` is `permitAll()` (not auth-gated)
- **Why**: Spring Boot dispatches every servlet forward (validation 400s, type-mismatch 400s, 404 on unmapped paths) through `/error` for body rendering. If the security filter auth-gates `/error`, every error response gets rewritten to 401 with `instance: "/error"`, masking the real failure shape. Persona audit (Dayo) caught it on `RejectWithEmptyReason` — empty body → 400 expected → seen as 401. Fixed by adding `/error` to the `permitAll` matcher list in `SecurityConfig`.
- **Cost**: `/error` itself is now anonymous-reachable. The endpoint returns a generic Spring error page when hit directly — no sensitive data, just `{"status":404,"error":"Not Found","path":"/error"}` or similar.
- **Revisit**: when we adopt a custom `ErrorController` that returns Problem+JSON on every dispatch (not just controller-thrown exceptions), we can re-evaluate whether anonymous reach to `/error` is still appropriate. Likely still fine, since the content is generic-shape, but worth a re-look.

---

## Deferred (not built — explicit Phase 14+ scope)

- `ListingPhoto`, `ListingSave`, `ListingLike`, `ListingReview` (engagement)
- `MessageThread`, `Message` (in-app messaging)
- `Comment` (public Q&A on listings)
- `AgentListing` (agent assignment + handshake)
- `Ad` (featured listings/agents)
- `view_count` on listings (engagement analytics)
- `DRAFT` / `PENDING` / `CLOSED_RENTED` / `CLOSED_SOLD` / `TAKEN_DOWN` listing states
- Counter-offer chain (`Offer.parent_offer_id`)
- No-show tracking on inspection requests
- Owner approve/decline of inspection requests
- Tiered admin roles
- Admin analytics dashboard
- Document/file storage (verification docs)
- DLT auto-replay tooling
- Dream AI conversational discovery
- Real-time messaging / SSE / WebSockets
- Multi-factor auth, refresh tokens, account lockout policies
- Multi-region / read replicas / sharded Postgres
