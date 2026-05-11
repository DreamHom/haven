# Post-audit improvements — Tier 1/2/3 cleanup + Phase 16 backlog

Single PR consolidating two batches of work on `feat/post-audit-improvements`:

1. **Phase 16** (already on branch from prior sprint): post-audit improvements
   surfaced by the silas-branch audit + Kafka audit — Kafka topic pinning,
   spring-boot-devtools, JPA auditing, R2 photo upload, admin analytics.
2. **Phase 16.5** (this PR's bulk): the 13 entries flagged as "lazy coding or
   ops polish worth fixing now" by the honest re-audit of all 126 prior
   TRADEOFFS entries.

## Summary

| Area | Item |
|---|---|
| 🔐 Security | RS256 JWT signing (no shared HMAC secret); `/auth/register` always 202 (anti-enumeration); seeded-admin env vars fail-loud on missing |
| 🛠 Behaviour | `POST /api/listings/{id}/report`; auto-decline sibling PENDING offers on accept; trust JPA auditing solely (drop manual `Instant.now()`) |
| 🧰 Tooling | MapStruct adoption (12 mappers); `DatabaseCleanupTestExecutionListener` replaces 21 per-IT `@AfterEach` blocks |
| 📈 Ops | `OutboxRelay` async publish (no `.get()`); `kafka.publish.duration` Timer; listener concurrency = topic partitions; `haven.outbox.dlt` depth gauge |

## Tier 1 — Security / correctness

### 1.1 — JWT: HS256 → RS256
- `JwtService` signs with private key, verifies with public key (PEM-encoded RSA, ≥ 2048 bits, mismatched-modulus rejected at startup).
- Config: `haven.jwt.private-key` + `haven.jwt.public-key` (no defaults).
- README documents the `openssl genpkey` workflow; `.env.example` updated.
- Test keypair lives at `src/test/resources/jwt/` and loads via `JwtTestKeys` utility.
- 14 `@WebMvcTest` `@TestPropertySource` blocks + `AbstractPostgresIT` `@DynamicPropertySource` rewritten to the new keys.

### 1.2 — `POST /auth/register` returns 202 in every branch
- Wire response is identical whether the email was newly registered or already taken — anti-enumeration.
- `AuthService.register` returns `void`; duplicates and TOCTOU collisions are silently swallowed (logged for ops).
- Drop now-dead `UserResponse` DTO + `EmailAlreadyRegisteredException`.
- OpenAPI updated; tests rewritten for the new contract.

### 1.3 — Seeded-admin env vars fail loud
- `application.yml`: bare `${ADMIN_EMAIL}` / `${ADMIN_PASSWORD_HASH}`, no fallback.
- A misconfigured deploy refuses to start — mirrors the `HAVEN_JWT_*` pattern.
- README + `.env.example` document the `htpasswd -nbBC 10 ...` one-liner for generating a bcrypt hash.

### 1.4 — `POST /api/listings/{id}/report`
- New `listingreport` feature package (model + repo + dto + service + controller + exception).
- V20 Flyway migration: `listing_reports` table with `(listing_id, reporter_user_id)` unique constraint.
- `ReportReason` enum: `SCAM | OFF_PLATFORM_FEES | STALE_OR_TAKEN | INAPPROPRIATE_CONTENT | OTHER`.
- Service writes the row + fans out one `LISTING_REPORTED` notification per admin.
- Tests: 4 unit + 5 IT covering 201, 400, 401, 404, 409.

## Tier 2 — Code quality

### 2.1 — Drop manual `Instant.now()` in services
- Every entity with `@CreatedDate` / `@LastModifiedDate` (Listing, Offer, Property, Notification, Comment, AdminAuditLog, User, AgentProfile, InspectionSlot, InspectionRequest, OutboxEvent, ListingReview, ListingReport) now relies on JPA auditing — single source of truth.
- Service unit tests rewritten to drop the now-irrelevant `assertThat(...getCreatedAt()).isNotNull()` assertions or to mock the repository to populate the field as JPA would.

### 2.2 — `DatabaseCleanupTestExecutionListener`
- Single `TRUNCATE … RESTART IDENTITY CASCADE` after every test method, registered on `AbstractPostgresIT`.
- Replaces 21 per-IT `@BeforeEach @AfterEach clean()` blocks (~232 lines deleted).
- Listener is registered FIRST in the `@TestExecutionListeners` list so its `afterTestMethod` runs LAST (after Spring's transactional rollback releases the connection).

### 2.3 — Auto-decline sibling PENDING offers on accept
- `OfferService.respond`: when one offer transitions to `ACCEPTED`, find every other PENDING offer on the same listing, flip them to `DECLINED`, fire one `OFFER_AUTO_DECLINED` notification per losing applicant — all in the same transaction.
- New `OfferRepository.findByListingIdAndStatusAndIdNot` for the sibling lookup.
- New `NotificationKind.OFFER_AUTO_DECLINED` with `{listingId, winningOfferId, reason}` payload.
- Decline path is unchanged.

### 2.4 — MapStruct adoption
- 12 hand-rolled `static toResponse(Entity)` helpers replaced by `@Mapper(componentModel = "spring")` interfaces.
- Mappers: `OfferMapper`, `AgentListingMapper`, `PropertyMapper`, `CommentMapper`, `UserAdminMapper`, `UserCredentialsMapper`, `InspectionSlotMapper`, `VerificationAdminMapper`, `ReviewMapper`, `ListingMapper`, `ListingPhotoMapper`.
- pom additions: `org.mapstruct:mapstruct` + `mapstruct-processor` + `lombok-mapstruct-binding` annotation processor.
- Service unit tests construct mappers via `new XxxMapperImpl()`; `@WebMvcTest` controller slices `@Import` the impl class.

## Tier 3 — Ops polish

### 3.1 — `OutboxRelay` publishes async
- Drop `kafkaTemplate.send(...).get()` in favour of `whenComplete((sr, ex) -> ...)`.
- Callback runs on the producer I/O thread; `markPublished(...)` opens a fresh transaction via `TransactionTemplate` because the originating tx is already closed.
- Failure on either side (publish ack or post-publish save) leaves the row unpublished — the next scheduled poll retries; consumer-side `event_id` dedup keeps it idempotent.

### 3.2 — `haven.kafka.publish.duration` Micrometer Timer
- `Timer.Sample` wraps every publish; tags: `topic` + `outcome=success|failure`.
- Surfaces in Prometheus alongside the existing `haven.outbox.unpublished` gauge.
- `OutboxRelayTest` asserts the timer fires with the expected tags.

### 3.3 — Listener concurrency = topic partition count
- `@KafkaListener(concurrency = "${haven.kafka.topic-partitions:3}")` on both listeners (`InspectionRequestedListener`, `OfferSubmittedListener`).
- Stays in sync with `KafkaTopicConfig`'s partition count by sharing the same property.

### 3.4 — `haven.outbox.dlt` depth gauge
- New `OutboxDltMetrics` component: one Gauge per DLT topic, tagged with `topic=`.
- Sources from Kafka `AdminClient.listOffsets(... OffsetSpec.latest())` — returns sum of partition end-offsets.
- `OutboxMetricsIT` extended with one assertion that confirms both expected DLT topic gauges are registered.

## Tests

```
mvn verify
→ Tests run: 309 (unit), Failures: 0, Errors: 0
→ Tests run: 101 (IT),   Failures: 0, Errors: 0
→ BUILD SUCCESS
```

Per-tier test counts:
| Tier | New tests | Modified tests |
|---|---|---|
| 1.1 (RS256) | 7 (rewrote `JwtServiceTest`) | 14 controller `@TestPropertySource` blocks renamed |
| 1.2 (register 202) | – | 6 `AuthServiceRegisterTest` cases + 6 `AuthControllerRegisterTest` cases |
| 1.3 (admin env) | – | `AbstractPostgresIT.@DynamicPropertySource` |
| 1.4 (report endpoint) | 4 unit + 5 IT | – |
| 2.1 (Instant.now drop) | – | 5 unit assertions reframed |
| 2.2 (test listener) | – | 21 ITs simplified |
| 2.3 (auto-decline) | 1 new + 4 reframed | – |
| 2.4 (MapStruct) | – | 4 service tests + 5 controller tests wired |
| 3.1+3.2 (relay) | – | `OutboxRelayTest` rewritten |
| 3.3 (concurrency) | – | – (config-only) |
| 3.4 (DLT gauge) | 1 new (`OutboxMetricsIT`) | – |

## Files of note

- `docs/plans/post-audit-tradeoff-cleanup.md` — the plan this PR executes.
- `docs/TRADEOFFS.md` — Phase 16.5 block at the bottom rewrites the lazy entries it resolves.
- `src/main/resources/db/migration/V20__create_listing_reports.sql` — the only schema change.
- `src/main/java/com/dreamhomes/haven/listingreport/` — entire new feature package.
- `src/main/java/com/dreamhomes/haven/common/outbox/{OutboxRelay,OutboxDltMetrics}.java` — relay rewrite + new metrics.
- `src/test/java/com/dreamhomes/haven/support/{DatabaseCleanupTestExecutionListener,JwtTestKeys}.java` — new test infra.

## Test plan

- [x] `mvn verify` green locally
- [ ] Manually exercise `POST /api/listings/{id}/report` against a live broker; confirm admin gets a notification
- [ ] Hit `/actuator/prometheus`; confirm `haven_kafka_publish_duration_seconds_count{topic="...",outcome="success"} > 0` after one inspection or offer submission
- [ ] Hit `/actuator/prometheus`; confirm `haven_outbox_dlt{topic="inspection.requested.v1.DLT"}` is present (value 0 in healthy state)
- [ ] Generate a fresh RSA keypair and boot the app with the new env vars
- [ ] Boot the app WITHOUT `ADMIN_PASSWORD_HASH` set; confirm Spring property resolution fails fast at startup

🤖 Generated with [Claude Code](https://claude.com/claude-code)
