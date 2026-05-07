# Trade-offs ledger

Every "we chose X over Y" decision worth remembering. Append as new ones land; revise when a defer becomes a do. **Format: choice → why → cost → revisit signal.**

Last updated after Phase 13 (photos + review takedown + counter-offers).

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
