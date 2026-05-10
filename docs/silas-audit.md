# silas's branch — deep audit

A line-by-line read of `origin/main` (silas's implementation of the same DreamHomes
PRD) to figure out what's actually built, what's scaffolding, and what — if anything
— is genuinely worth bringing into our codebase.

---

## The headline finding

**silas's branch is mostly scaffolding.** The package layout, file naming, and
class signatures are conventional and correct. But somewhere between 60–70% of
the methods inside those classes are empty bodies, return hardcoded placeholders,
or do nothing meaningful. The earlier comparison table I drew (the one that
listed Cloudinary, KafkaTopicConfig, AuditConfig, and AdminAnalytics as "things
to port from silas") was based on file presence, not file contents. Reading the
contents shows those files are empty stubs.

This is important because it changes the merge plan: there is **nothing to port
wholesale**. The handful of things that look like good ideas are good ideas
*we'd build from scratch*, not code we'd lift from his branch.

---

## File inventory at a glance

| | silas (`origin/main`) | us (`lukasio`) |
|---|---|---|
| Java files | 110 | ~470 (incl. tests) |
| Tests | 1 (default `contextLoads` with autoconfig disabled) | 390 (real ITs + units, all passing) |
| Flyway migrations | **0** | 18 (`V1`..`V18`) |
| Config classes with real code | **1 of 9** (only `SecurityConfig`, and it `permitAll()`s everything) | 4, all functional |
| Security classes with real code | **0 of 4** | full JWT chain |
| Spring Boot version | 3.5.14 | 3.3.5 |
| `application.properties` size | 8 lines + 4-line `secrets.properties` (committed!) | ~110 lines `application.yml`, env-driven |

---

## The empty-stub catalog

Concrete files I opened that contain no implementation:

### `config/` — 8 of 9 are empty

```java
package com.dreamhomes.haven.config;

public class CloudinaryConfig {
    
}
```

Same shape (no fields, no methods, no annotations) for: `AuditConfig`,
`CloudinaryConfig`, `JwtConfig`, `KafkaConsumerConfig`, `KafkaProducerConfig`,
`KafkaTopicConfig`, `MapperConfig`, `RestConfig`. The class names suggested
intent; the bodies confirm none of it shipped.

The single non-empty config is `SecurityConfig`:
```java
http
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
    .httpBasic(Customizer.withDefaults());
```
Every endpoint is public. CSRF off. The fallback is HTTP Basic — **not JWT**,
despite the README claiming JWT.

### `security/` — 4 of 4 are empty

```java
public class JwtAuthenticationFilter {
    
}
```

Same for `JwtTokenProvider`, `CustomUserDetailsService`, `UserPrincipal`. There
is no JWT implementation in his branch. The pom doesn't even depend on JJWT.

### `domain/user/service/AuthService.authenticate(...)` is empty

```java
@Transactional(readOnly = true)
public void authenticate(LoginRequest req) {

}
```

Returns void, validates nothing, mints no token. The controller calls it and
then returns the literal string `"TODO"` as the bearer token:

```java
@PostMapping("/login")
public AuthResponse login(@Valid @RequestBody LoginRequest req) {
    authService.authenticate(req);
    return new AuthResponse("TODO", "Bearer");
}
```

### `domain/admin/service/` — placeholder bodies

```java
@Service
public class AdminListingService {
    public void approveListing(Long listingId) {

    }
}

@Service
public class AdminUserService {
    public void moderateUser(Long userId, UserModerationRequest req) {

    }
}

@Service
public class AdminAnalyticsService {
    public AnalyticsSummaryResponse summary() {
        return new AnalyticsSummaryResponse(0, 0, 0, 0, 0);   // hardcoded zeros
    }
}
```

So when I previously called out "port AdminAnalytics" as worth doing — what
exists in silas's branch is a hardcoded `0,0,0,0,0` response. Inspiration for
the *idea*, not source material.

---

## What silas *does* actually implement

A small, real list — the parts of his branch that have code that runs:

### `User` entity + `UserRepository` + register flow
- `User`: id, email, passwordHash, role, firstName, lastName, displayName.
- No `tokenVersion` (no revocation), no `suspendedAt` (no admin moderation flag),
  no `identityVerifiedAt` (no badge), no phone, no `createdAt`/`updatedAt`.
- `AuthService.register(...)`: works — checks email uniqueness, BCrypts password,
  saves the row.
- Login is non-functional (see above).

### `Property` + `PropertyRepository` + create/get
- Functional CRUD: create + get-by-id.
- **Bug**: `PropertyController.create` reads `ownerId` from the request body —
  any caller can create a property "for" any user. We take ownerId from the JWT;
  silas can't, because he has no JWT.

### `Listing` + create / get / update
- Same pattern. Functional. No `list` / browse endpoint, no public discovery,
  no pagination. Status transitions are unguarded — any value goes through
  `setStatus(...)`.

### `InspectionRequest` + `InspectionSlot` + Kafka event flow
- Real implementation of the booking + slot model.
- **Direct Kafka publish**, no transactional outbox:
  ```java
  // After saving the InspectionRequest:
  eventProducer.publishInspectionRequested(topic, event);
  ```
  This is the dual-write race PRD §7 explicitly worries about. If the DB tx
  rolls back after the Kafka publish, the consumer creates a notification for a
  non-existent inspection. If the DB tx commits and the Kafka publish fails,
  the notification is silently lost. Our outbox fixes both.
- No event-id dedup on the consumer side.
- Topic name: `INSPECTION_REQUESTED` (no version suffix — vs our
  `inspection.requested.v1` which versions the schema).

### `Offer` + submit + Kafka event flow
- Same pattern as inspection. Direct dual-write. No counter-offer chain
  (no `parent_offer_id`).

### `Comment` + create + listByListing
- Basic CRUD. No soft-delete, no parent-child threading, no auth on either
  endpoint.

### `Verification` + submit + review
- Basic CRUD. **Crucially missing**: badge-stamping side effects on approve.
  Setting `status = APPROVED` updates the verification row but does not stamp
  `users.identity_verified_at` etc. The badge is just a row state, not a
  cross-aggregate signal.

### `Notification` + create + listForUser
- Functional CRUD.
- `InspectionNotificationConsumer` listens, creates notifications for inspection
  events. Same for offers. No idempotence; replaying the same Kafka event
  creates duplicates.

### `GlobalExceptionHandler`
- Real implementation. 4 custom RuntimeExceptions
  (`ResourceNotFoundException`, `ConflictException`, `UnauthorizedException`,
  `ValidationException`) → status mapping. Custom `ErrorResponse` record:
  ```java
  public record ErrorResponse(Instant timestamp, int status, String error,
                              String message, String path) {}
  ```
  Not RFC 7807 ProblemDetail. Different wire shape than ours, less standard.

---

## What silas's app would actually do if you booted it today

It probably doesn't boot. Three hard blockers:

1. **`spring.jpa.hibernate.ddl-auto=validate`** + **0 Flyway migrations** = Hibernate
   tries to validate that the schema matches the entities, finds no schema, fails
   to start.
2. The placeholder JWT secret in the committed `secrets.properties`
   (`change-me-to-a-secure-secret-of-at-least-32-bytes`) — though silas has no
   secret-validation logic, so this would technically NOT block startup unlike
   ours.
3. The single test (`DreamhomesHavenApplicationTests.contextLoads`) explicitly
   excludes JPA, datasource, and Kafka autoconfig:
   ```java
   @TestPropertySource(properties = {
       "spring.autoconfigure.exclude=" +
           "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
           "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
           "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
   })
   ```
   The test passes because all the real wiring is disabled. It proves nothing
   about the actual application.

This is consistent with a project that hasn't been booted end-to-end. The
scaffolding was created, the obvious file names were laid down, but the actual
implementation never followed.

---

## What silas got right (worth keeping in mind)

Real wins, even if execution didn't follow:

1. **Package-by-feature with classic layered subfolders** — `domain/<feature>/{controller,service,dto,model,repository}`. We adopted exactly this shape in the recent restructure. **silas's intuition here was sound.**
2. **Custom `RuntimeException` hierarchy** mapped via `@RestControllerAdvice` to typed responses. Right pattern. We do similar (`DomainException` + RFC 7807 ProblemDetail), more rigorously.
3. **Records for DTOs everywhere** — clean, immutable, no boilerplate. We do this too.
4. **Lombok `@RequiredArgsConstructor` for dependency injection** — the modern style; cleaner than explicit constructors. We use this consistently.
5. **`@Value`-driven Kafka topic names** — `@Value("${kafka.topics.inspection-requested:INSPECTION_REQUESTED}")` lets ops override per environment without code changes. We use a similar pattern via `application.yml`.
6. **`spring-boot-devtools` in the pom** — gives hot reload during local dev. **We don't have this.** Worth adding (5 minutes, real productivity).
7. **Spring Boot 3.5.14** vs our 3.3.5 — newer. No urgency to bump but not harmful either.
8. **Three Drawio diagrams checked in** under `images/` (system-architecture, ERD, sequence-kafka-flows). We have similar under `docs/diagrams/`. Both branches treat docs-as-code, which is right.

---

## What silas got wrong (independent of execution gaps)

Architectural calls that would still be wrong even if the code was complete:

1. **Direct Kafka publish in service methods, no outbox** — the PRD §7
   reliability story explicitly calls out the dual-write race; silas's design
   doesn't address it. This is a deliberate-feeling choice, not an oversight,
   because the producer + consumer code is real. The consequence is that
   missed notifications are inevitable under failure.
2. **Topic names without version suffixes** (`INSPECTION_REQUESTED` vs
   `inspection.requested.v1`). Future schema evolution will hurt — adding a
   field to the event payload requires every consumer to deserialize the new
   version, with no parallel-deploy path.
3. **`secrets.properties` committed to git** — even though the values are
   placeholders, the *file pattern* is dangerous. The first contributor who
   puts a real secret in there leaks it.
4. **`SecurityConfig.permitAll()`** — if the JWT plumbing were finished, the
   security config doesn't actually wire it. Every endpoint would still be
   public. This is the kind of bug that lives in production for a year because
   tests don't cover it.
5. **Custom error response shape** instead of RFC 7807 ProblemDetail. Limits
   client interop, no `type` URI for programmatic branching, no standard
   `instance` field. We chose ProblemDetail; that was the right call.
6. **`User.displayName = firstName`** — silently sets displayName to firstName
   on register and update. Either he never thought about it, or he decided to
   defer the choice and forgot. Either way, the UI would look strange.
7. **Pulled `flyway-core` into the pom but wrote zero migrations**. With
   `ddl-auto=validate` set, this is a contradiction the runtime would reject.

---

## Reassessment of the original "4 things to port" plan

Earlier (in `PR.md`) I scoped four follow-up PRs:

| # | Originally pitched as | What's actually in silas's branch |
|---|---|---|
| 1 | Port `KafkaTopicConfig` (pin partition counts) | **Empty class.** No `NewTopic` beans defined. |
| 2 | Port JPA `AuditConfig` (`@CreatedDate`/`@LastModifiedDate`) | **Empty class.** No `@EnableJpaAuditing`. |
| 3 | Port `CloudinaryConfig` + image upload pipeline | **Empty class.** No Cloudinary SDK in the pom. No upload service. |
| 4 | Port `AdminAnalytics` endpoints | **Hardcoded zeros**: `return new AnalyticsSummaryResponse(0, 0, 0, 0, 0)`. |

So **none of these are ports.** They're all "build from scratch," with silas's
class names as a sketch of intent, nothing more.

The four ideas remain *good ideas worth building*. They just need to be reframed
in the PR plan and the commit messages — credit silas for surfacing the gap, but
own the implementation as ours.

---

## What we'd build from scratch, in order

| # | Build | Why now | Effort |
|---|---|---|---|
| 1 | **`NewTopic` beans pinning partition counts + replication factor** for `inspection.requested.v1` and `offer.submitted.v1` | Surfaced in my Kafka audit; guards against partition-count drift on auto-create. | ~30 min |
| 2 | **`spring-boot-devtools` dependency** | silas has it; we don't. Local-dev hot reload is real productivity. Tiny pom change. | ~5 min |
| 3 | **JPA auditing** (`@EnableJpaAuditing` + `@CreatedDate` / `@LastModifiedDate` on entities) | Replaces the manual `Instant.now()` everywhere. ~20 entities affected; mostly mechanical. | ~2 hrs |
| 4 | **R2 (S3-compatible) image upload pipeline** for `POST /api/listings/:id/photos` | We have the schema (ListingPhoto), just need the actual upload + URL hosting. silas's `CloudinaryConfig` confirmed this is a real gap, not a niche concern. | ~half day |
| 5 | **Admin analytics dashboard** (real numbers from real queries) | Aggregates we'd actually surface: count of pending verifications, listings by status, offers in flight, suspended users. silas's hardcoded `0,0,0,0,0` confirms the *idea* but not a *starting point*. | ~half day |

Each lands as its own PR with tests written first.

---

## What we have that silas doesn't (the unambiguous wins)

A short list, for the bootcamp narrative:

- **Working JWT auth** with `tokenVersion` revocation. silas has none.
- **18 Flyway migrations** that actually evolve a real schema. silas has zero.
- **Transactional outbox** + after-commit relay + DLT routing + retry backoff +
  event-id dedup on consumer side. silas dual-writes.
- **390 passing tests** — unit + IT + Testcontainers Postgres + EmbeddedKafka.
  silas has 1 stub.
- **5 features silas didn't build at all**: `photo`, `agentlisting`, `engagement`
  (saves), `review`, `auth` as a feature (his auth is a half-implemented service
  inside `user/`). Plus our `verification` actually stamps badges; his sets a status.
- **RFC 7807 `ProblemDetail` everywhere**, with a configurable `type` URI
  namespace pointing at real error docs.
- **Bucket4j rate limiting** on auth endpoints.
- **`PublicCacheHeadersInterceptor`** so public discovery routes are CDN-cacheable.
- **`@PreAuthorize` role gates** + ownership checks at the service layer.
- **Configurable type URIs** (`HAVEN_ERRORS_TYPE_BASE`) + per-error explainers
  in `docs/errors/`.
- **OpenAPI/Scalar UI** with summary + description + examples + every error
  response documented per endpoint.
- **6 persona docs** in `docs/users/` mapping every user story to its endpoints
  + tests + error scenarios.

---

## Recommendation

1. **Keep the merge `-s ours`.** No part of silas's branch is shippable as-is.
2. **Reframe the follow-up PRs from "port" → "build."** Update `PR.md` to drop
   any language suggesting code transfer; describe each as "build feature X
   that the parallel branch identified as needed but didn't implement."
3. **Add `spring-boot-devtools` while we're touching things** (5-min PR).
4. **Be generous in the PR description**: silas's structural intuition was
   right, even where the implementation didn't follow. Credit the package
   layout and the feature inventory; don't credit the empty methods.
5. **Don't try to merge silas's commits into ours later.** His branch will keep
   having empty-class-named features. We'll just keep `-s ours`-ing as he
   pushes.

The honest one-liner for any reviewer or future-you: *I read silas's branch,
believed the class names too quickly the first time, and corrected the plan
once I read the bodies. The structural ideas were good; the implementation
needs to be ours.*

---

*Audit performed by reading every config, every security class, and at least
one file from each `domain/<feature>/` folder on `origin/main` at commit
`1d1ff87`. Findings cross-referenced against our codebase as of the layered-
subfolder restructure on `lukasio`.*

---

## Addendum — `origin/silas` (unmerged work)

Caught after the first pass: `origin/silas` is silas's working branch and has
**one commit ahead of `origin/main`**: `3f128d5 feat: use spring flyway to monitor sql database creation`.

Worth re-reading because it adds the one piece I'd called out as completely
missing — Flyway migrations.

### What `3f128d5` actually adds

1. **`V1__init_schema.sql`** (85 lines) — 10 tables: `users`, `properties`,
   `listings`, `agent_listings`, `offers`, `inspection_slots`,
   `inspection_requests`, `comments`, `verifications`, `notifications`.
2. **`V2__add_foreign_keys.sql`** (79 lines) — 15 indexes on FK columns + 13
   `FK ... ON DELETE RESTRICT` constraints + 1 unique constraint on
   `agent_listings(agent_id, listing_id)`.
3. **Schema cleanups** on a few entities (`Notification` slimmed from 45 → 27
   lines, mostly removing manually-managed columns now stamped as defaults).
4. **`application.properties` rewrite** — drops the credential split, points at
   `haven_db` with `haven_user`/`haven_pass` baked into a still-committed file
   (which is worse than the prior split, not better — but it's intentional now).

### What `3f128d5` does NOT fix

Same gaps as the merged work:

- **`AuthService.authenticate(...)`** is still empty even on this latest commit.
  Login still doesn't function.
- All 8 empty config classes remain empty.
- All 4 empty security classes remain empty.
- `SecurityConfig.permitAll()` remains.
- Still no outbox table in the schema.
- Still no `tokenVersion`, `suspendedAt`, `identityVerifiedAt` on `users`
  (matches his entity, but means the trust + revocation features can't exist).
- Still 1 test, still excludes JPA + datasource + Kafka.

### How silas's schema compares to ours

His 10 tables map roughly to a subset of ours, but much simpler:

| Table | silas's columns | What ours adds |
|---|---|---|
| `users` | id, email, password_hash, role, first_name, last_name, display_name | `token_version`, `suspended_at`, `identity_verified_at`, `phone`, `created_at`, `updated_at`, `deleted_at` |
| `properties` | id, owner_id, address_line1, city, state_name, country, status | `bedrooms`, `bathrooms`, `size_sqm`, `description`, `documents_verified_at`, audit timestamps |
| `listings` | id, property_id, type, status, price, title | `owner_id`, `description`, `currency`, `version` (optimistic lock), audit timestamps |
| `agent_listings` | id, agent_id, listing_id | `requested_by_owner_id`, `decision_reason`, `status` enum, `requested_at`, `decided_at` |
| `offers` | id, listing_id, applicant_id, amount, status, created_at | `currency`, `message`, `parent_offer_id` (counter-offer chain), `proposed_by_user_id`, `version`, `updated_at` |
| `inspection_slots` | id, listing_id, agent_id, start_at, end_at | the GiST EXCLUDE constraint preventing overlapping active slots |
| `verifications` | id, subject_user_id, property_id, document_url, type, status, created_at | `submitter_user_id` (vs only subject), `target_user_id`, `document_refs` JSON, `decided_by_admin_id`, `decided_at`, `decision_reason` |
| `notifications` | id, user_id, type, payload, created_at | `kind` + `source` enums, `read_at`, `event_id` for outbox dedup |

His schema is missing entirely: `outbox_events`, `listing_photos`,
`listing_saves`, `listing_reviews`, `admin_audit_log`, `agent_profiles`. Six
tables of ours have no equivalent on his side.

### Updated verdict

The migrations on `origin/silas` confirm two things:

1. **silas was working towards a runnable app** — the schema work is real, not
   placeholder. The empty methods elsewhere are a "haven't gotten there yet"
   state, not abandonment.
2. **Even at his most-current commit, the merge plan stays the same.** There's
   no Flyway migration to port — ours covers everything his does and 2x more.
   His schema is conceptually simpler in ways that map *backwards* from where
   we are (e.g. no `tokenVersion`, no outbox, no soft-delete columns).

The "build it ourselves" plan in the original audit stands. Nothing new to
port from `origin/silas`. The acknowledgement is to be fair: I was wrong to
say "0 Flyway migrations" — that was true of `origin/main` but not the
unmerged silas branch.

### Process lesson

When auditing a parallel implementation, check the **branches** the
collaborator works on, not just the integration branch. `origin/main` is
where merged work lives; `origin/silas` (or `origin/<author-name>`) is where
in-flight work lives. Reading both gives the honest picture of what the
person has actually written.
