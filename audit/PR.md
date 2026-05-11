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

Then started fixing what the audit surfaced. This PR ships:

1. **The read-side slice** — 5 `GET /…/mine` endpoints that every persona
   independently named as their #1 frustration.
2. **23 targeted fixes** mapped 1:1 to specific persona complaints — bugs,
   schema drift, missing actions, doc cleanup, enum extensions.
3. **4 net-new DB migrations** to support new lifecycle states the audit
   exposed as missing.
4. The audit tooling itself (Bruno collections, persona-agent rules, HTML
   reports) so future sessions can re-run the same UX walkthrough.

Verification: [`audit/reports/v2-verification.md`](../audit/reports/v2-verification.md)
covers every finding from every persona with a live curl command proving
the fix. **433 tests green (332 unit + 101 IT, 0 failures).**

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
| Unit (Mockito + WebMvc) | **332** | 0 fail / 0 error |
| Integration (Testcontainers Postgres + Kafka) | **101** | 0 fail / 0 error |
| **Total** | **433** | green |

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

## ❗ Critical findings still pending (separate PRs)

These are flagged in the summary but **not** addressed in this PR. Ranked
by cross-persona impact:

1. 🔴 **`GET /admin/audit-logs`** — Dayo CRITICAL. Audit data is being written but no reader exists. Every moderation guarantee is currently unfalsifiable.
2. 🔴 **`GET /admin/listing-reports`** — Dayo CRITICAL. User reports persisted, no admin queue. Same write-only-moderation shape as the audit log.
3. 🟠 **`GET /agents?q=&verified=true`** — Biodun's marketplace-killer. Owners can't find agents to invite.
4. 🟠 **`POST /verifications/{id}/files`** — multipart upload for NIN / C of O. Every persona. R2 pipeline already plumbed for photos; needs to extend to verifications.
5. 🟠 **`GET /listings` filters** silently ignored (`?location`, `?priceMin`, `?bedrooms`, `?sort`). Temi, Ngozi, Emeka.
6. 🟠 **Trust-signal denormalization on `ListingResponse` + `PublicUserProfile`** — `documentsVerifiedAt`, `closedDealCount`, `medianResponseMinutes`, `assignedAgentId`. Ngozi's whole top-5, plus Emeka's #1.
7. 🟠 **Sync notification on every user action** — verification submitted, inspection booked, offer submitted, report filed. Ngozi: "silence is what scammers feel like."
8. 🟡 `intent: enum [RENT, BUY, RENT_TO_BUY]` on offers — Ngozi (rent-to-buy + Moniepoint).
9. 🟡 Bulk operations (`POST /properties/bulk`, `POST /listings/bulk`) — Biodun.
10. 🟡 Listing marketing fields (`title`, `description`, `headline`, `handoverDate`) — Biodun, Amaka.
11. 🟡 `GET /admin/users?email=` + `?suspended=true` — Dayo's missing user search.
12. 🟡 `reason` body on reactivate-user + re-publish-listing — Dayo's symmetry argument.
13. 🟡 Auto-JWT on register OR clearer 202 body copy — Amaka, Temi.
14. 🟡 Real-time push (SSE / WebSocket) + notification preferences — Temi.
15. 🟡 Logout `?scope=device|all` — needs per-token blocklist subsystem.

## Test plan

- [x] `mvn verify` green (332 unit + 101 IT, 0 failures)
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
