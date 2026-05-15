# Post-audit trade-off cleanup — 13 items on `feat/post-audit-improvements`

All work lands on `feat/post-audit-improvements`. One commit per item (or per
tight cluster). `mvn verify` green between every commit. TRADEOFFS.md +
PR.md updated at the end.

## Context

Post the honest audit of all 126 TRADEOFFS entries, 13 items were classified
as either lazy coding or ops polish worth fixing now (vs. legitimate scoped
trade-offs we'll defer). This plan executes them.

5 items already shipped on this branch: NewTopic beans, devtools, JPA
auditing, R2 photo upload, admin analytics. Tests at 398 passing.

## Tier 1 — Security / correctness

### 1.1 RS256 JWT signing
- Generate RSA keypair (one-time openssl, document the command)
- Add `haven.jwt.private-key` + `haven.jwt.public-key` config (PEM strings via env)
- `JwtService` signs with private, verifies with public (`Keys.keyPairFor(RS256)`)
- Fail-loud constructor validation if keys missing/malformed (RSA-only, ≥ 2048 bits, matching modulus)
- `JwtServiceTest` uses a known test keypair fixture
- TRADEOFFS entry under Auth & security captures the chosen shape + the cost (PEM env vars vs flat secret)

### 1.2 `POST /auth/register` → 202 Accepted (no enumeration)
- Service still does the work synchronously, but returns 202 with empty body
- Existing 409 "email taken" path becomes 202 too (silent)
- Update controller `@ResponseStatus`, OpenAPI, and `AuthControllerRegisterTest` + `AuthFlowEndToEndIT`
- TRADEOFFS update: this closes the "register leaks user existence" entry

### 1.3 Fail loudly on missing seeded-admin password env
- Today: silent fallback. Make it mirror the JWT_SECRET pattern — throw
  `IllegalStateException` at startup if `HAVEN_SEED_ADMIN_PASSWORD` is unset
  and the seed runs
- Add an integration assertion that a misconfigured app refuses to boot

### 1.4 `POST /api/listings/{id}/report` endpoint
- Backlog item from Ngozi persona; feeds admin moderation queue
- New `ListingReport` entity + repo + Flyway migration (V20)
- `ListingReportController.report(@PathVariable, @Valid @RequestBody ReportListingRequest)`
- Service writes the row + fires a `LISTING_REPORTED` notification to all admins
- Tests: service unit test + IT covering 201, 401, 404, 409 (duplicate per-user-per-listing)
- Admin read endpoint NOT in scope here — separate ticket

## Tier 2 — Code quality

### 2.1 Delete manual `Instant.now()` from services/builders
- Audit every `.createdAt(Instant.now())` / `.updatedAt(Instant.now())` in services + tests
- Remove from production code — JPA auditing populates them
- Test seed helpers can keep them (deterministic seed data is fine)
- Verify by deleting, running `mvn verify`, fixing what breaks

### 2.2 Lift `@AfterEach` cleanup to `@TestExecutionListener`
- Today every `*IT` repeats a FK-ordered `deleteAll()` block
- Build `DatabaseCleanupTestExecutionListener` registered on `AbstractPostgresIT`
- Either reflect over JPA metamodel OR keep a hardcoded ordered list in one place
- Delete the per-test `@BeforeEach @AfterEach clean()` blocks
- Verify all 398 tests still pass; bonus: cleaner test files

### 2.3 Auto-decline sibling PENDING offers on accept
- `OfferService.respond()`: when transitioning chosen offer → ACCEPTED, find
  all other PENDING offers on the same listing and mark them `DECLINED_AUTO`
  (or reuse `DECLINED` with a reason field — TBD when we look at the enum)
- Fire `OFFER_DECLINED` notifications to each losing applicant
- Add `OfferServiceRespondTest` case + IT case asserting siblings flip
- TRADEOFFS update: closes the "accept leaves sibling offers stuck PENDING" entry

### 2.4 MapStruct adoption
- Add `org.mapstruct:mapstruct` + annotation processor to pom
- Convert ~14 static `toResponse` / `toView` / `toCommand` methods to `@Mapper` interfaces
- Per package: e.g. `OfferMapper`, `ListingMapper`, `UserMapper`, `CommentMapper`, etc.
- Spring component model (`componentModel = "spring"`)
- Update controllers/services to inject the mappers
- Update unit tests that previously called the static methods
- TRADEOFFS entry: rewrite the "static mappers — fine for now" line

## Tier 3 — Ops polish

### 3.1 Drop sync `.get()` from `OutboxRelay.onOutboxRowReady`
- Today: `kafkaTemplate.send(...).get()` blocks the relay thread
- Switch to `whenComplete((sr, ex) -> { if (ex != null) ...; else markPublished(...); })`
- Adjust the existing IT — it asserts publish completes; switch the assertion to "eventually published" via Awaitility (already on classpath via spring-kafka tests)

### 3.2 Add `kafka.publish.duration` Micrometer Timer
- Wrap the publish call in `Timer.Sample.start(meterRegistry).stop(timer)`
- Tag by topic
- Add a unit test that asserts the timer is registered with expected tags after one publish

### 3.3 Listener concurrency = `${haven.kafka.topic-partitions:3}`
- Today: default 1
- Wire `concurrency` on the @KafkaListener container factory from the same
  property as the topic partitions (so they stay in sync)
- No new test (config-only); the existing consumer ITs cover behavior

### 3.4 `haven.outbox.dlt` depth gauge
- Register a Micrometer Gauge that reads `outbox_event` rows where
  `status = 'DLT'` (or wherever DLT-routed events land in our schema)
- Polled (Gauge with supplier closure over `outboxRepository.countByStatus(DLT)`)
- Test: assert the gauge is registered and reflects DB state after we
  artificially route a row to DLT status

## Final

### 13. mvn verify + docs
- Full `mvn verify` clean
- TRADEOFFS.md: rewrite the 9 lazy entries this plan resolved, add Phase 16.5
  block summarizing what shipped
- PR.md: extend with the new sections under "What's in the PR"
- One commit at the end for the docs

## Conventions

- Package-by-feature; no new top-level packages without justification
- TDD-first: failing test before implementation, every service method
- Test what we own; skip framework configuration tests
- One commit per Tier item, message format `tier-X.Y: <imperative>`
- `mvn verify` green at every commit boundary
