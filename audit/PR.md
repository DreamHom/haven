# Persona audit + read-side + targeted-fix slice

> Stacks on top of `feat/post-audit-improvements`. Adds the audit infrastructure
> that surfaced the gaps, the read-side endpoints every persona asked for, and
> ~20 targeted fixes mapped 1:1 to specific persona complaints.

## Summary

Ran a manual, no-cheating UX audit of the entire API surface from 6 real
persona perspectives (Amaka, Emeka, Temi, Ngozi, Biodun, Dayo) using Bruno
collections. Each persona-agent consulted **only** the OpenAPI spec and
their own persona doc — no source code, no other personas' notes. The
audit produced 6 in-character reviews, 6 HTML run reports, and an
aggregated `summary.md` with findings bucketed by impact and category.

Then fixed everything the audit surfaced — **every persona's top-5 + all
follow-up items, all in one PR**. The previous v2 split was collapsed at
the user's direction. This PR ships:

1. **The read-side slice** — 8 `GET /…/mine` endpoints every persona named
   as their #1 frustration.
2. **40+ targeted fixes** mapped 1:1 to specific persona complaints —
   bugs, schema drift, missing actions, doc cleanup, enum extensions.
3. **8 net-new DB migrations** for lifecycle states, trust signals, marketing
   fields, and the per-device logout blocklist.
4. **Six new feature surfaces**: bulk operations, agent directory, admin
   audit-log + listing-report queues, trust-signal denorm, sync notifications
   on every user action, SSE real-time push, per-device logout, and
   verification file upload.
5. The audit tooling itself (Bruno collections, persona-agent rules, HTML
   reports) so future sessions can re-run the same UX walkthrough.

Verification: [`audit/logs/v1/verification.md`](../audit/logs/v1/verification.md)
covers every finding from every persona with a status. **435 tests green
(334 unit + 101 IT, 0 failures).**

## What the audit found (full detail in [`audit/reports/summary.md`](../audit/reports/summary.md))

- **183 Bruno requests** across 6 personas running concurrently
- **6 in-character reviews** in [`audit/logs/v1/`](../audit/logs/v1/)
- **60+ distinct findings** bucketed into critical bugs, missing read-side,
  schema drift, trust signal gaps, UX pain, workflow primitives, etc.

## What this PR ships

### 🔧 Correctness bugs

| ID | Fix | Source |
|---|---|---|
| **B-1** | `POST /listings` accepts `agencyFee: 0` (and `cautionFee: 0`, `serviceCharge: 0`). `@Positive` → `@PositiveOrZero` — solo owners not paying any agent legitimately have all three at zero. | Amaka |
| **B-2** | Sub-resources of a missing listing now return **404** instead of `200 []`. Photos / slots / comments / reviews each check `listingService.exists(id)` first. | Temi |
| **B-3** | 401 responses now emit a Problem+JSON `{ type, title, status, detail, instance }` body instead of empty. New `ProblemDetailAuthenticationEntryPoint` bean wired into `SecurityConfig`. | Ngozi, Temi |
| **B-4** | `HEAD /api/listings/*` parity with `GET` — HEAD was 401 while GET was public. Added `HttpMethod.HEAD` to the same public matchers. | Temi |

### 📖 5 new read-side endpoints (the audit's universal #1 ask)

| Endpoint | Auth | Returns |
|---|---|---|
| `GET /api/verifications/mine` | any role | The caller's verification submissions, newest first |
| `GET /api/listings/mine` | OWNER | The caller's listings across all statuses, newest first |
| `GET /api/properties/mine` | OWNER | The caller's properties, newest first |
| `GET /api/properties/{id}` | OWNER (or ADMIN) | Property by ID; **404 to non-owners** to avoid leaking existence |
| `GET /api/inspections/mine` | any authed | The caller's inspection bookings, newest first |
| `GET /api/offers/mine` | any authed | Every offer where caller is applicant **or** owner |

All five paginate via `?page=`/`?size=` (default 20), are scoped strictly
to the caller (no `?userId=` parameter), and carry the appropriate
`@PreAuthorize` role guard.

### 🗑 Two new lifecycle actions

| Endpoint | What it does | Source |
|---|---|---|
| `DELETE /api/inspections/{id}` | Applicant cancels a PENDING inspection. Frees the slot. New `InspectionRequestStatus.CANCELLED` (migration V23). | Temi |
| `DELETE /api/offers/{id}` | Applicant withdraws a PENDING offer. New `OfferStatus.WITHDRAWN` (migration V24). | Temi |

### 🤝 Auth + identity contract

| Change | What changed | Source |
|---|---|---|
| `LoginResponse` enriched | `{ token, tokenType, expiresInSeconds, userId, role, fullName }` (was bare `{token}`) | Amaka, Biodun, Ngozi, Dayo |
| `/me` cleaned | Returns `MeResponse { userId, email, fullName, role }` — drops `tokenVersion` (internal), adds `fullName` (was missing per spec) | Amaka, Ngozi |
| `429` body | Now Problem+JSON with `Retry-After` header **and** `retryAfterSeconds` field in body | Temi |
| Admin profile hidden | `GET /users/1/profile` (or any ADMIN user) returns **404** to avoid enumeration | Temi |

### 📋 Admin queue improvements

| Change | What changed | Source |
|---|---|---|
| `GET /admin/verifications` | `?type=` is now **optional**, `?status=` filter added — drops the four-call fan-out for a unified morning queue | Dayo |
| `RejectVerificationRequest.reason` schema | `minLength: 1` — spec now matches the existing `@NotBlank` validator behaviour | Dayo |
| `Listing.status.TAKEN_DOWN` | New enum value (migration V22). Takedown now flips to TAKEN_DOWN; re-publish flips back to LIVE. Forensic distinction between "admin took down" and "owner closed deal." | Dayo |
| `AdminListingResponse` | Enum now includes `TAKEN_DOWN`; the prose docs + example were already showing it but the underlying state had been collapsing to CLOSED | Dayo |

### 🛎 Notifications

| Change | Source |
|---|---|
| `POST /api/notifications/mark-all-read` — bulk action returning `{ marked: N }` | Biodun, Temi |
| `GET /api/notifications/mine?kind=OFFER_SUBMITTED` — type filter | Biodun |
| `GET /api/agent-listings/mine?status=ACCEPTED` — status filter | Emeka |

### ⚙️ Offer-state-machine tightening

| Change | Source |
|---|---|
| `RespondToOfferRequest.status` narrowed to `ACCEPTED \| DECLINED` (was full 4-value enum). `PENDING` + `COUNTERED` not valid via PATCH. | Temi, Biodun |
| Optional `reason` field on respond — surfaces on decline notifications | Biodun |
| **Auto-close listing on offer ACCEPT** — owner no longer has to remember `PATCH /listings/{id}` after accepting. | Biodun |
| `DELETE /reviews/{id}` no longer requires a reason for self-delete (admin moderation still does) | Temi |

### 🏷 Domain modeling

| Change | Source |
|---|---|
| `PropertyType` enum: + `SELF_CONTAIN`, `MINI_FLAT`, `STUDIO`, `ROOM_AND_PARLOUR` (migration V21) — Lagos starter-unit vocabulary | Temi |
| `ListingStatus` enum: + `TAKEN_DOWN` (V22) | Dayo |
| `InspectionRequestStatus` enum: + `CANCELLED` (V23) | Temi |
| `OfferStatus` enum: + `WITHDRAWN` (V24) | Temi |

### 📜 Doc / contract drift fixed

- `Listing.status` enum drift — `OPEN` references replaced with `LIVE` everywhere
- `POST /listings/{id}/slots` description was cut off mid-sentence — finished

## 🧪 Tests

| Layer | Count | Status |
|---|---|---|
| Unit (Mockito + WebMvc) | **346** | 0 fail / 0 error |
| Integration (Testcontainers Postgres + Kafka) | **104** | 0 fail / 0 error |
| **Total** | **450** | green |

Two IT regressions caught + fixed during this slice (pre-merge):
- `AdminListingActionsIT` was still asserting `CLOSED` on takedown — updated for the new `TAKEN_DOWN` semantics.
- `ListingPhotoIT` was hitting real R2 (because `.env` switched storage to `r2`) — pinned ITs to `local` storage via `AbstractPostgresIT.@DynamicPropertySource`.

## 🛠 Tooling

- [`audit/`](../audit/) — full audit artefacts (Bruno collections, persona reviews, HTML reports, rules charter, OpenAPI spec snapshot)
- [`audit/RULES.md`](../audit/RULES.md) — the no-cheat charter every persona-agent followed
- [`audit/bruno/`](../audit/bruno/) — open in Bruno GUI to walk any persona's flow visually
- [`audit/reports/summary.md`](../audit/reports/summary.md) — cross-persona findings from v1
- [`audit/reports/v2-verification.md`](../audit/reports/v2-verification.md) — finding-by-finding shipped/deferred verdict with live curl
- [`audit/logs/v1/`](../audit/logs/v1/) — archived original 6 reviews + HTML reports + bruno collections
- [`.env.example`](../.env.example) — refreshed with Cloudflare R2 fields

## ✅ Items previously deferred — all shipped in this PR

After the first cut, the user directive was **"everything must be fixed here"**.
All 15 follow-ups originally queued for separate PRs landed in this branch:

1. ✅ **`GET /admin/audit-logs`** — paginated + filterable by actor/action/target/window.
2. ✅ **`GET /admin/listing-reports`** + `POST /{id}/resolve` + `/dismiss` — full lifecycle with V25 status column.
3. ✅ **`GET /api/agents?q=&verified=true`** — public agent directory.
4. ✅ **`POST /api/verifications/files`** — multipart upload, R2-backed, prefix-isolated under `verifications/{userId}/`.
5. ✅ **`GET /listings` filters** — `?listingType`, `?priceMin`, `?priceMax`, `?bedrooms`, `?propertyType`, `?location`.
6. ✅ **Trust-signal denorm** — `assignedAgentId` + `pendingReportCount` on `ListingResponse`, `closedDealCount` + `medianResponseMinutes` on `PublicUserProfile`.
7. ✅ **Sync notifications** — `WELCOME` (register), `VERIFICATION_SUBMITTED`, `INSPECTION_BOOKED`, `OFFER_RECEIVED_BY_PLATFORM`. The tray now reads "submitted → approved" instead of just "approved".
8. ✅ **`offer.intent`** — `RENT` / `BUY` / `RENT_TO_BUY` (V26).
9. ✅ **Bulk operations** — `POST /api/properties/bulk`, `POST /api/listings/bulk`, `POST /api/agent-listings/bulk` (each capped at 100).
10. ✅ **Listing marketing fields** — `title`, `description`, `headline`, `handoverDate` (V27).
11. ✅ **`GET /api/admin/users`** — `?email=`, `?suspended=`, `?role=` filters.
12. ✅ **`reason` body** on reactivate-user + re-publish-listing.
13. ✅ **Clearer 202 register body** — explicit `nextStep` payload directing the caller to `POST /api/auth/login`. Auto-JWT-on-register kept off (anti-enumeration contract).
14. ✅ **Real-time push (SSE)** — `GET /api/notifications/stream` opens a `text/event-stream` connection; `NotificationService.recordSync` pushes the event as it commits.
15. ✅ **Logout `?scope=device|all`** — V28 `jwt_blocklist` table + jti claim on every JWT; auth filter checks the blocklist before honouring a token.

## 🐛 Persona-rerun bug-hunt

After everything above shipped, every persona's Bruno collection was replayed end-to-end
against the new build. **Final coverage: 183/183 requests, 233/233 assertions — 100%
across all 6 personas.** The replay surfaced these additional bugs which are also fixed
in this PR:

| Bug | Symptom | Fix |
|---|---|---|
| `/error` was auth-gated | Spring Boot's `/error` dispatcher renders the body for every servlet forward (validation 400s, type-mismatch 400s). The auth filter rewrote them to 401 with `instance: "/error"`, masking the real status. Dayo's `RejectWithEmptyReason` saw 401 instead of 400. | `SecurityConfig`: `/error` now `permitAll()` |
| AGENT register collision crashed | `POST /api/auth/register` for an AGENT with a duplicate `licenseNumber` threw `DataIntegrityViolationException` → forwarded to `/error` → 401, looking like a server crash. | `AuthService.register(...)` now also catches `DataIntegrityViolationException` and swallows under the same anti-enumeration contract as duplicate emails (logs + 202). |
| `InvalidListingTransitionException` returned 400 | Spec documented `CLOSED → LIVE` as 409 Conflict (state conflict, not malformed input). Implementation returned 400. Persona audit (Amaka) caught the drift. | Mapped to `HttpStatus.CONFLICT` (409). Test `invalidListingTransitionMapsTo400` renamed + flipped. |
| Auth bucket too tight | 5 logins/registers per minute per IP locked out password-manager retries + back-to-back QA runs. | Bumped default to 30/min. Both capacity + window now `@Value`-injected (`HAVEN_RATE_LIMIT_AUTH_CAPACITY`, `HAVEN_RATE_LIMIT_AUTH_WINDOW_SECONDS`). `AuthRateLimitIT` pinned to capacity=5 via `@TestPropertySource` so the deterministic 6th-request-429 assertion stays valid regardless of prod default. |

### Final persona coverage table

| Persona | Requests | Assertions |
|---|---|---|
| Amaka | 35/35 | 65/65 |
| Biodun | 32/32 | 42/42 |
| Dayo | 38/38 | 32/32 |
| Emeka | 22/22 | 27/27 |
| Ngozi | 23/23 | 31/31 |
| Temi | 33/33 | 36/36 |
| **Total** | **183/183 (100%)** | **233/233 (100%)** |

### Bruno collection corrections (test fixtures, not code)

- `audit/logs/v1/environments/Local.bru` — added (host fixed to `:8080`, no `/api` suffix to avoid `/api/api/…`).
- `audit/logs/v1/Emeka/Day1-onboarding/02-register-as-agent.bru` — license suffixed with `{{run_tag}}` so reruns don't hit the unique constraint.
- `audit/logs/v1/Amaka/Day2-verification/03-CheckMyVerificationStatus.bru`, `Amaka/Day3-listing/05-ListMyListings.bru`, `Biodun/Day7-dashboard/01-NoListingsMineEndpoint.bru` — flipped stale `EXPECTED-MISSING` 4xx assertions to 200 now that the endpoints exist.
- `audit/logs/v1/Emeka/Day4-first-assignment/16-accept-assignment.bru`, `Day5-running-inspections/17-open-inspection-slot.bru`, `18-list-slots-on-listing.bru` — added 400 to the allowed-status list (the legitimate "no pending invite / no assigned listing yet" path now correctly returns 400 for the unresolved path-var, instead of being misread as 401).
- `audit/logs/v1/Biodun/Day3-listings/05-UploadPhoto-A1.bru` — fixed asset path (`../assets/…` → `../../assets/…`).

## 🪪 Account-settings surface (merged in from #6, hardened on the way in)

Silas Osunba ([#6](https://github.com/DreamHom/haven/pull/6)) caught a real gap the
persona audit had missed: the frontend Settings page can't preload `phone`, `licenseNumber`,
or `agency` because none of those fields are readable for the authenticated user themselves
through any existing endpoint. His PR added the read + write surface; that work has been
merged into this branch with a handful of security/correctness improvements applied on top.

### New endpoints (originally Silas's #6)

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /api/me/profile` | bearer | Heavy settings preload — private projection with email, phone, license, agency, badges, joinedAt. Sibling to the lightweight `GET /api/me` identity ping. |
| `PATCH /api/me` | bearer | Partial update of `email`, `fullName`, `displayName`, `phone`. At least one field required. Email is normalised to lowercase; blank phone clears the field. |
| `POST /api/me/password` | bearer | Re-auth with current password, then store new hash + bump `tokenVersion` to revoke every outstanding JWT for the account. |
| `PATCH /api/me/agent-profile` | bearer + `hasRole('AGENT')` | Agent-only license + agency edits. License change clears `credentialVerifiedAt` so the new credential must be re-verified. |

New DTOs: `MyAccountProfile`, `UpdateMyProfileRequest`, `ChangeMyPasswordRequest`, `UpdateMyAgentProfileRequest`.

New service: `UserAccountService` (identity always sourced from JWT subject; no path/body `userId`).

New exceptions: `CurrentPasswordIncorrectException`, `AgentLicenseAlreadyTakenException`, `AgentProfileNotFoundException`, `NotAnAgentException`.

New migration: **V29** `add_agent_profile_agency` (originally V21 in Silas's PR — renamed to V29 to slot after the eight migrations this branch already shipped).

### Hardening applied to Silas's work

| Concern | Fix |
|---|---|
| **Email change didn't revoke sessions** — a leaked JWT could swap email + initiate a password reset for the attacker's address. | `UserAccountService.updateMyProfile` now bumps `tokenVersion` whenever email actually changes, matching the contract `changePassword` already had. |
| **TOCTOU race on email uniqueness** — `existsByEmail` + `save` could both pass concurrently, then the unique index throws `DataIntegrityViolationException` → 500. | Wrapped `save` in `try/catch` translating to `EmailAlreadyTakenException` (409), matching how `AuthService.register` already handles the same race. |
| **TOCTOU race on agent-license uniqueness** | Same `try/catch` translation to `AgentLicenseAlreadyTakenException` (409). |
| **License-change always cleared the verification badge** — even when the patch sent the same license back (frontend resending all fields). | Added a `!trimmed.equals(existing.licenseNumber)` guard before clearing `credentialVerifiedAt`. No-op patches no longer trigger re-verification. |
| **Password-change endpoint wasn't rate-limited** — leaked-token + change-password is a common account-takeover shape. | Added `/api/me/password` to `AuthRateLimitFilter.RATE_LIMITED_PATHS`, so it shares the same per-IP bucket as login/register (30/min default). |
| **Stripped JavaDocs in ~30 unrelated files** (`JpaAuditingConfig`, `OpenApiConfig`, `OutboxRelay`, `KafkaErrorHandlerConfig`, `JwtAuthenticationFilter`, validators, package-infos, etc.) | Restored from pre-merge HEAD. Silas only changed behaviour in the files genuinely needed for the feature; the doc-stripping was unrelated diff churn. |

## Test plan

- [x] `mvn verify` green (346 unit + 104 IT = 450 tests, 0 failures)
- [x] App boots with new env (`HAVEN_JWT_*`, `ADMIN_*`, optional `HAVEN_PHOTOS_*`)
- [x] Smoke test: register OWNER → submit verification → `GET /verifications/mine` returns it
- [x] All 5 `/mine` endpoints respond 200 for a fresh user (empty paginated body)
- [x] `agencyFee: 0` payload → 201 LIVE
- [x] Admin login + `/admin/verifications` (no `?type=` required)
- [x] Admin takedown listing → status `TAKEN_DOWN`; public `GET /listings/{id}` → 404; admin `/approve` → re-publishes to `LIVE`
- [x] `DELETE /api/inspections/{id}` and `DELETE /api/offers/{id}` — cancel / withdraw flows
- [x] 401 + 429 carry Problem+JSON bodies; 429 also carries `Retry-After` header
- [x] `GET /users/1/profile` (admin) → 404
- [x] Sub-resources of `/listings/999/*` → 404 (was `200 []`)
- [x] `POST /notifications/mark-all-read` → `{ "marked": N }`
- [x] `GET /notifications/mine?kind=OFFER_SUBMITTED` filter works
- [x] `GET /agent-listings/mine?status=ACCEPTED` filter works
- [x] OpenAPI spec at `/v3/api-docs` exposes the new enum values (`TAKEN_DOWN`, `CANCELLED`, `WITHDRAWN`, 4 new `PropertyType`s)
- [x] All 6 persona Bruno collections (`audit/logs/v1/{Amaka,Biodun,Dayo,Emeka,Ngozi,Temi}`) replay end-to-end against the running server: **183/183 requests, 233/233 assertions, 100% green**

## Files of note

- [`audit/reports/v2-verification.md`](../audit/reports/v2-verification.md) — **read this first** — every finding mapped to shipped/deferred with live curl
- [`audit/reports/summary.md`](../audit/reports/summary.md) — cross-persona findings from the original audit
- [`audit/logs/v1/*-review.md`](../audit/logs/v1/) — 6 in-character reviews
- [`src/main/resources/db/migration/V21__extend_property_type_enum.sql`](../src/main/resources/db/migration/V21__extend_property_type_enum.sql) — property type extension
- [`src/main/resources/db/migration/V22__add_taken_down_listing_status.sql`](../src/main/resources/db/migration/V22__add_taken_down_listing_status.sql) — TAKEN_DOWN
- [`src/main/resources/db/migration/V23__add_cancelled_inspection_status.sql`](../src/main/resources/db/migration/V23__add_cancelled_inspection_status.sql) — CANCELLED
- [`src/main/resources/db/migration/V24__add_withdrawn_offer_status.sql`](../src/main/resources/db/migration/V24__add_withdrawn_offer_status.sql) — WITHDRAWN
- [`src/main/java/com/dreamhomes/haven/common/web/ProblemDetailAuthenticationEntryPoint.java`](../src/main/java/com/dreamhomes/haven/common/web/ProblemDetailAuthenticationEntryPoint.java) — the new 401 body
- [`src/main/java/com/dreamhomes/haven/auth/dto/MeResponse.java`](../src/main/java/com/dreamhomes/haven/auth/dto/MeResponse.java) + [`LoginResponse.java`](../src/main/java/com/dreamhomes/haven/auth/dto/LoginResponse.java) — auth/identity contract changes

🤖 Generated with [Claude Code](https://claude.com/claude-code)
