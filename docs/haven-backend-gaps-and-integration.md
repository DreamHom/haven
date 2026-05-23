# Haven backend gaps, desired capabilities, and Vista integration

This document collates **Vista-facing limitations**: what the **Vista** app still stages locally, which **Haven** APIs exist today, and what remains **truly open** on the backend or in product scope.

**Canonical API contract:** **`GET /v3/api-docs`** on a Haven deployment (springdoc; paths are under **`/api`**). **`GET /scalar.html`** serves the same contract interactively. This repo does **not** ship a checked-in OpenAPI YAML; if Vista keeps a frozen export, it lives in the Vista tree (for example `vista/docs/haven-api-docs-1.0.1.yaml`) and must be regenerated from **`/v3/api-docs`** when Haven ships.

**Keeping Haven + Vista aligned:** changelog and matrix → [`docs/vista/integration-log.md`](vista/integration-log.md).

**Vista integration surface (paths are in the Vista repo, not here):** `lib/api.ts`, `app/api/[...path]/route.ts`, `lib/seed/public-data.ts`, `lib/*-dashboard.ts`.

---

## 0. How this inventory was built (so you can re-audit)

Searches and patterns used in **Vista**:

| Pattern / component | Why it matters |
| --- | --- |
| `PublicApiNotice` | User-visible callout on public routes when data or API behavior is incomplete or degraded. |
| `PrototypeNotice` (owner / agent / admin primitives) | Yellow-style product banners: local staging, missing endpoints, or design-only flows. |
| `SectionCard` descriptions with “Haven”, “local”, “prototype”, “backend” | Softer banners embedded in page chrome. |
| `EmptyHint` + `backendUnavailable` | Public empty states when Haven browse fails. |
| `toast.*(backend\|Haven\|support)` | Inline stubs for actions blocked on API. |
| `ErrorPanel` bodies mentioning **Haven** or generic load failures | Surfaces that depend on Haven for primary data. |
| `localStorage` in `lib/*-dashboard.ts`, `lib/auth-store.ts` | Features persisted only in the browser until Vista migrates to Haven APIs already shipped. |
| `ForgotPasswordForm` / `ResetPasswordForm` | **Must call Haven** — endpoints exist; forms were historically client-only. |
| Comments in `dream-ai-chat.tsx`, `dream-ai/match.ts`, `lib/api.ts` | Planned swap to real endpoints / transports. |

Re-run in **Vista**: `rg -i "haven|prototype|publicapinotice|backendunavailable|stored locally|not exposed|waiting on backend" --glob "*.{tsx,ts}"`.

---

## 1. Executive summary

**Haven** now exposes a large slice of what this document originally tracked as “missing”: password reset, soft delete, notification preferences, listing richness (pets/utilities, negotiable, virtual tour URL, coordinates), owner/agent inspection transitions, agent-scoped listing PATCH and slot creation, admin listing catalogue and moderation snapshot, comment flag queues, platform settings, ad campaign CRUD (without full billing vertical), listing leads with owner reveal and admin read, optional httpOnly JWT cookie, applicant/owner avatar upload, and agent marketing gallery with validation and reorder.

**What still separates Vista from “done”** is mostly: **(a)** Vista **UI migration** off `localStorage` and stale `PrototypeNotice` / `PublicApiNotice` copy, **(b)** **email delivery** and full **BFF/session** hardening, **(c)** product gaps **not yet modeled in Haven** (Dream AI **streaming / full-catalog search** / richer chat, agent secure handoff without raw PII, verification “request more info” loop, ads billing/delivery), and **(d)** **operational** reliability of public browse behind the Vista proxy.

See **Appendix A** for the authoritative route-level mapping.

---

## 2. Banner & notice inventory (user-visible in Vista)

> **Stale copy warning:** Rows below quote **historic** Vista strings. Many describe gaps **closed on Haven `main`**. Before changing Haven, update Vista notices and wire `lib/api.ts` — then delete the banner. **Appendix A** lists the Haven side.

### 2.1 `PublicApiNotice` (public discovery & trust)

| Vista surface | Historic concern | Haven today (API) |
| --- | --- | --- |
| `app/(public)/listings/[id]/page.tsx` | Pets/utilities missing; map approximate | **`petsAllowed`**, **`utilitiesNote`** on listing; **`latitude`/`longitude`** on embedded property summary (nullable on legacy rows). |
| `app/(public)/map/page.tsx`, `listings-explorer`, `compare`, Dream AI shell | Browse empty / unreliable | **`GET /api/listings`** (public). Empty = proxy/env/data — not “no endpoint”. |
| `app/(public)/owners/[id]/page.tsx` | No public owner bio | **`publicBio`** (+ trust aggregates) on **`GET /api/users/{id}/profile`**. “Richer story” beyond one bio field is still product/`partial`. |

### 2.2 `PrototypeNotice` (workspaces / admin)

| Vista surface | Historic concern | Haven today (API) |
| --- | --- | --- |
| `admin-settings-page.tsx` | No platform config API | **`GET` / `PATCH /api/admin/platform-settings`**. |
| `admin-listings-page.tsx` | No admin catalogue | **`GET /api/admin/listings?status=&page=&size`**. |
| `admin-comments-page.tsx` | No flag queue | **`GET/POST …/admin/comment-flags`** (resolve/dismiss). |
| `admin-ads-page.tsx` | Ads local only | **Campaign CRUD** + admin review **`/api/admin/ad-campaigns/{id}`** — **billing/delivery/reporting** still out of scope / `partial`. |
| `agent-listing-management-page.tsx` | Agent cannot PATCH / slots | **Assigned ACCEPTED agent** may **`PATCH /api/listings/{id}`** (marketing only) and **`POST …/slots`**. |
| `agent-inspections-page.tsx` | No agent decisions | **`POST …/inspections/{id}/agent/complete`**; broader **reschedule/decline** paths still **`partial`** if product requires them. |
| `agent-offers-page.tsx` | Agent cannot counter | **Agent** may **`PATCH /api/offers/{id}`** and **`POST …/counter`** when assigned. |
| `agent-leads-page.tsx` | Narrow PII | **Listing leads** are **owner** workflow + **admin** read; **agent secure handoff** without raw PII is still **product / Haven future** (see §11). |
| `owner-inspections-page.tsx` | Approve/decline/no-show missing | **`POST …/owner/approve`**, **`…/owner/decline`**, **`…/mark-no-show`**. |
| `owner-leads-page.tsx` | Contacts not exposed | **`POST/GET …/listings/{id}/leads`**, **`POST …/reveal`**; **`GET /api/admin/listings/{id}/leads`** for admins. |
| `owner-new-property-page.tsx` | Draft local | Wizard draft may stay local; **persisted** fields go to Haven on submit. **Negotiable** + **virtual tour URL** are on listing APIs. |
| `owner-dashboard-home-page.tsx` | Verification nudge | **Verification APIs** exist; copy is UX. |

### 2.3 Other prominent “Haven / backend” copy (SectionCards, hints, badges)

| Vista surface | Historic concern | Haven today |
| --- | --- | --- |
| `listings/[id]` | Inspection fee badge | Product/billing alignment (§17) — not a CRUD gap. |
| `profile-page.tsx` (applicant) | Photo local | **`POST /api/me/avatar`**, **`profileImageUrl`**. |
| `owner-profile-page.tsx` | Bio/photo local | **`publicBio`**, **`POST /api/me/avatar`**. |
| `agent-profile-page.tsx` | Marketing local | **`GET/POST/PATCH/DELETE /api/me/agent-marketing`** + **`agentMarketingGallery`** on public profile. |
| `owner-property-detail-page.tsx` | Property read-only | **`PATCH /api/properties/{id}`** (owner/admin). |
| `owner-new-property-page.tsx` | Negotiable / tour | **`priceNegotiable`**, **`virtualTourUrl`** on **`POST/PATCH /api/listings`**. |
| Settings pages | Prefs + delete local | **`notificationPreferences`** on **`PATCH /api/me`**; **`DELETE /api/me`**. |

---

## 3. Authentication, session, and password recovery

| Gap | What we want | Haven today | Vista today |
| --- | --- | --- | --- |
| **JWT in `localStorage`** | httpOnly cookie or BFF + CSRF | **Optional** **`haven.auth.jwt-cookie.*`**; filter accepts **Bearer or cookie**. Full BFF still **`partial`**. | `lib/auth-store.ts` |
| **Forgot / reset password** | Rate-limited reset + email | **`POST /api/auth/forgot-password`**, **`POST /api/auth/reset-password`**. **Email outbound not wired** in Haven. | Forms historically **did not call Haven** — **wire to API** and handle 202/204. |
| **Login when already signed in** | Skip redundant login | **`GET /api/me`** — `200` = still authenticated. | Optional `LoginForm` check. |
| **Registration** | 202 + anti-enumeration | **`POST /api/auth/register`** | Already wired. |

**Integration ask:** Vista implements real forgot/reset against Haven; enable cookie mode when Vista is ready to stop duplicating JWT in JS; add **email provider** for reset tokens in non-dev environments.

---

## 4. File uploads & media (what exists vs what is missing)

### 4.1 Already wired to Haven (multipart / real routes)

| Flow | Haven endpoints (prefix `/api`) |
| --- | --- |
| Verification documents | `POST /verifications/files`, `POST /verifications` |
| Owner listing gallery | `POST /listings/{listingId}/photos` |
| Applicant / owner / agent avatar | `POST /me/avatar` |
| Agent marketing gallery | `POST /me/agent-marketing` (multipart), `GET`, `PATCH /me/agent-marketing/order`, `DELETE /me/agent-marketing/{id}` |

### 4.2 Still open (product / backend)

| Gap | Notes |
| --- | --- |
| **Listing media beyond current model** | **`virtualTourUrl`** exists; **floor plans, multi-asset video gallery**, etc., are **not** a separate Haven resource yet. |
| **MIME / size policy** | Agent gallery: **JPEG/PNG/WebP/GIF** + **`haven.photos.agent-marketing.max-bytes`** (see `application.yml`). Listing photos use existing storage config. |

---

## 5. Client-side persistence (Vista — migrate to Haven)

Haven **already** exposes server-side replacements for most buckets below. The table documents **where Vista still persists in `localStorage`** until dashboard code is migrated.

| Data | Vista files (indicative) | Haven replacement (prefix `/api`) |
| --- | --- | --- |
| Admin platform settings | `lib/admin-dashboard.ts`, `admin-settings-page.tsx` | **`GET` / `PATCH /admin/platform-settings`** |
| Admin + agent ads | `lib/admin-dashboard.ts`, ads pages | **`/me/ad-campaigns`**, **`/admin/ad-campaigns/{id}`** |
| Admin comment flags | `lib/admin-dashboard.ts`, `admin-comments-page.tsx` | **`/admin/comment-flags`** + resolve/dismiss |
| Notification preferences (all roles) | `*-dashboard.ts`, settings pages | **`PATCH /me`** (`notificationPreferences` JSON) |
| Profile / marketing “drafts” | profile pages | **Persisted fields** via **`PATCH /me`**, **`PATCH /me/agent-profile`**, **`/me/agent-marketing`**, **`publicBio`** |
| Property wizard draft | `owner-new-property-page.tsx` | Optional local draft OK; **submit** uses Haven **property + listing** APIs |

**Integration ask:** Vista deletes `readFromStorage` / `write` helpers **after** switching each screen to the Haven routes in the right column.

---

## 6. Dream AI

| Layer | Haven today | Still open |
| --- | --- | --- |
| **Inventory** | **`POST /api/dream-ai/suggestions`** + **`POST /api/dream-ai/turns/stream`**: with **`HAVEN_ANTHROPIC_API_KEY`**, bounded **LIVE** catalogue → **Anthropic Claude 3.5 Haiku**; ids **re-validated**. **Without** the key: **stub** (`location=`). Persisted threads, **JSONB** messages, **`client_message_id`** idempotency, **SSE** MVP (see OpenAPI + [`dream-ai-capabilities.md`](dream-ai-capabilities.md)). | Full-catalog **semantic search** / embeddings; **provider token streaming**; **caching**; stricter **quotas** than `haven.dream-ai.rate-limit` |
| **Reasoning** | Single-turn **`AssistantTurnV1`** (`reply` / `clarify` / `compare` / `no_results` / `error`) + optional markdown | Multi-turn with **citations**; **function/tool** rows; richer safety |
| **Transport** | **JSON POST** + **SSE** (`trace` / `delta` / `final` / `problem`) | Chunked tokens from Anthropic; **NDJSON** alternative |
| **Auth / rate limits** | JWT + **per-user Dream AI** token bucket (`DreamAiRateLimitFilter`) | Additional abuse tiers, geo/IP heuristics |

---

## 7. Connectivity and public inventory

| Gap | Haven today | Notes |
| --- | --- | --- |
| Public browse empty / unreachable | **`GET /api/listings`**, **`GET /api/agents`**, etc. | Vista **`backendUnavailable`** = proxy, CORS, env, or zero seed data — fix ops + UI fallbacks. |

---

## 8. Listings, geography, and rich listing fields

| Gap | Haven today | Still open |
| --- | --- | --- |
| **Coordinates** | Property create + **`PATCH /api/properties/{id}`**; embedded in listing cards | Backfill / UX for null legacy rows |
| **Pet rules & utilities** | **`petsAllowed`**, **`utilitiesNote`** | — |
| **Marketing description** (distinct 4th field) | **`title`**, **`description`**, **`headline`**, **`handoverDate`** | Separate **SEO-only** field only if product insists |
| **Agent PATCH / slots** | Done for **ACCEPTED** agent | — |
| **Admin listing catalog** | **`GET /api/admin/listings`** | — |
| **TAKEN_DOWN admin snapshot** | **`GET /api/admin/listings/{id}/moderation-snapshot`** | — |

---

## 9. Inspections lifecycle

| Gap | Haven today | Still open |
| --- | --- | --- |
| **Owner approve / decline / no-show** | **`POST …/owner/approve`**, **`…/owner/decline`**, **`…/mark-no-show`** | — |
| **Agent decisions** | **`POST …/agent/complete`**, **`POST …/agent/reschedule`**, **`PATCH …/agent/extras`** | Broader agent-only decline/cancel if PRD expands |
| **Slot RBAC** | **Owner or assigned agent** **`POST …/slots`** | — |
| **Applicant claim** | **`POST /inspections`** | — |

---

## 10. Offers and negotiations

| Gap | Haven today |
| --- | --- |
| **Agent-side mutations** | **Assigned ACCEPTED agent**: **`PATCH /api/offers/{id}`**, **`POST …/counter`** |

---

## 11. Leads, contact reveal, and secure handoff

| Gap | Haven today | Still open |
| --- | --- | --- |
| **Owner reveal + inbox** | **`POST/GET …/listings/{id}/leads`**, **`POST …/reveal`** (paginated owner list); **unique** `(listing, applicant)` in DB | Vista: remove reveal **toast** stub |
| **Admin moderation read** | **`GET /api/admin/listings/{id}/leads`** (full contact) | — |
| **Agent pipeline without raw PII** | — | **Workflow / masked bridge** — not the same as owner listing leads; design + API TBD |

---

## 12. User profiles, settings, and account lifecycle

| Gap | Haven today | Still open |
| --- | --- | --- |
| **Notification preferences** | **`PATCH /api/me`** (`notificationPreferences`) | Vista migration off localStorage |
| **Account deletion** | **`DELETE /api/me`** (soft delete) | Vista danger-zone wiring |
| **Photos / bio / agent marketing** | See §4.1 | **Richer public owner page** than `publicBio` alone = product/`partial` |

---

## 13. Admin, ads, comments, verification workflow

| Gap | Haven today | Still open |
| --- | --- | --- |
| **Comment flag queue** | User flag + admin queue APIs | — |
| **Verification request-more-info** | — | **Structured admin → submitter loop** (not built) |
| **Ads lifecycle** | Draft → review + admin patch | **Billing, delivery, reporting** |
| **Platform configuration** | **`/admin/platform-settings`** JSON | Vista migrate |
| **Admin listing catalog** | **`GET /api/admin/listings`** | — |

---

## 14. Comments and public Q&A

| Gap | Haven today |
| --- | --- |
| **Moderation pipeline** | **`POST …/comments/{commentId}/flag`** + **admin comment-flags** |

---

## 15. Toasts and disabled actions (stubbed UX in Vista)

| Location | Fix in Vista |
| --- | --- |
| Settings delete toasts | Call **`DELETE /api/me`** |
| Owner leads reveal toast | Call **`POST …/leads/{id}/reveal`** |
| Admin verification “request more info” | **Haven endpoint TBD** (§13) — keep stub until spec’d |

---

## 16. Error panels explicitly tied to Haven loads

Unchanged: Vista **ErrorPanel** retry flows depend on Haven returning consistent **problem+json**; list endpoints support **pagination** (`Page` envelope).

---

## 17. Miscellaneous product copy

| Item | Notes |
| --- | --- |
| Inspection fee badge | Align **copy** with actual billing policy — not a generic “Haven missing” signal. |
| Applicant notifications | Keep **notification kinds** + deep links stable as events grow (`NotificationKind` enum is contract). |

---

## 18. Suggested order of work

### Haven (backend) — remaining verticals

1. Dream AI: full-catalog search + provider streaming + TOOL traces (beyond current JSON + SSE MVP + `haven.dream-ai.rate-limit`).
2. Listing **media** extras (e.g. multipart video upload to storage, if product outgrows URL rows + `floor_plan_url`).  
3. **Verification request-more-info** workflow.  
4. **Ads** billing / delivery / reporting.  
5. **Agent lead handoff** without raw PII (if product commits).

### Vista (frontend) — unblock UX

1. Wire **forgot/reset** forms to Haven.  
2. Remove **`localStorage`** for items in **Appendix A** marked **done**.  
3. Refresh **`PublicApiNotice` / `PrototypeNotice`** per §2.  
4. Regenerate Vista’s bundled **`haven-api-docs-*.yaml`** (if maintained) from **`GET /v3/api-docs`** after each Haven release.

---

## 19. Source index (grep anchors)

`Haven`, `Haven v1.0.1`, `haven`, `backendUnavailable`, `PublicApiNotice`, `PrototypeNotice`, `stored locally`, `not exposed`, `waiting on backend`, `staged`, `GET /listings`, `approximate`, `ForgotPasswordForm`, `ResetPasswordForm`, `readAdmin`, `DEFAULT_ADMIN`, `uploadOwnerListingPhoto`, `runAssistant`, `dream-ai`.

---

## 20. Future-facing code comments (non-UI, Vista repo)

| Location | Intent |
| --- | --- |
| `lib/seed/listings.ts` | Shape doc / fallback when real `/api/listings` is canonical. |
| `lib/seed/collections.ts` | Possible future `/collections` read API. |
| `lib/types.ts`, `lib/api.ts` | DTO lockstep; `FormData` for uploads. |

---

## Appendix A — Haven API parity matrix (track `main`)

Paths use the **`/api`** prefix as in OpenAPI. **Status:** `done` = shipped; `partial` = stub or incomplete vertical; `n/a` = Vista-only/ops.

| § | Topic | Haven routes / artifacts | Status |
| --- | --- | --- | --- |
| 3 | Password reset | `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` | `partial` (no email delivery in Haven) |
| 3 | Optional httpOnly JWT cookie | `haven.auth.jwt-cookie.*` (+ `domain`, `same-site`), `JwtCookieService`, filter reads cookie; reset clears cookie | `partial` (no BFF) |
| 3 | Session probe | `GET /api/me` | `done` |
| 4 | Avatar | `POST /api/me/avatar`, `profileImageUrl` on user DTOs | `done` |
| 4 | Agent marketing | `GET/POST/PATCH/DELETE /api/me/agent-marketing` (+ order); `agentMarketingGallery` on `GET /api/users/{id}/profile` | `done` |
| 4 | Listing media beyond tour | `floorPlanUrl` + `virtualTourUrl` on listings; `GET/POST /api/listings/{id}/videos`, `DELETE /api/listings/videos/{id}` (URL pointers) | `done` |
| 5–7 | Platform settings | `GET`, `PATCH /api/admin/platform-settings` | `done` |
| 5–7 | Ad campaigns | `POST/GET/PATCH /api/me/ad-campaigns`, admin `GET/PATCH /api/admin/ad-campaigns/{id}` | `partial` (no billing vertical) |
| 5–7 | Comment flags | `POST …/comments/{id}/flag`; `GET …/admin/comment-flags` + resolve/dismiss | `done` |
| 5–7 | Notification prefs | `notificationPreferences` on `PATCH /api/me` | `done` |
| 6 | Dream AI | `POST /api/dream-ai/suggestions` + `POST /api/dream-ai/turns/stream` — **Haiku** when key set (bounded catalogue); **stub** without; **AssistantTurnV1**, SSE, idempotency, rate limit, moderation (see [`dream-ai-capabilities.md`](dream-ai-capabilities.md)) | `partial` (no full-DB RAG / provider token streaming / TOOL traces) |
| 7 | Public browse | `GET /api/listings`, `GET /api/agents`, … | `done` (reliability = ops + Vista proxy) |
| 8 | Coordinates | Property create + `PATCH /api/properties/{id}`; `PropertySummary` on listings; Flyway `V37` backfills null lat/long to Lagos centroid | `done` (owners should refine real pin via PATCH) |
| 8 | Pets / utilities | On listing create/update/`ListingResponse` | `done` |
| 8 | Negotiable / tour | `priceNegotiable`, `virtualTourUrl` | `done` |
| 8 | Agent PATCH listing | `PATCH /api/listings/{id}` for ACCEPTED agent (marketing only) | `done` |
| 8 | Agent slots | `POST /api/listings/{id}/slots` (owner or agent) | `done` |
| 8 | Admin listing catalogue | `GET /api/admin/listings` | `done` |
| 8 | TAKEN_DOWN snapshot | `GET /api/admin/listings/{id}/moderation-snapshot` | `done` |
| 9 | Owner inspection actions | `POST …/inspections/{id}/owner/approve`, `/owner/decline`, `/mark-no-show` | `done` |
| 9 | Agent inspection complete | `POST …/inspections/{id}/agent/complete` | `done` |
| 9 | Agent reschedule / extra | `POST /api/inspections/{id}/agent/reschedule`, `PATCH /api/inspections/{id}/agent/extras`; `agentExtras` on inspection responses | `done` |
| 10 | Agent offers | `PATCH /api/offers/{id}`, `POST …/counter` for assigned agent | `done` |
| 11 | Listing leads | `POST/GET …/listings/{id}/leads`, `POST …/reveal`; admin `GET …/admin/listings/{id}/leads`; `LISTING_LEAD_SUBMITTED` notification | `done` |
| 11 | Agent PII-safe handoff | — | `todo` / product |
| 12 | Soft delete | `DELETE /api/me` | `done` |
| 12 | Public bio | `publicBio` on `GET /api/users/{id}/profile`; `ownerPublicBio` on `ListingResponse` (browse, detail, mine, admin catalog, **`POST /api/listings`**, **`POST /api/listings/bulk`**, **`PATCH /api/listings/{id}`**) | `done` (API); `partial` only if Vista wants richer owner “story” modules |
| 13 | Verification RMI loop | — | `todo` |
| 13 | Ads billing | — | `todo` |

---

*Appendix A should be updated whenever `docs/vista/integration-log.md` changelog ships new routes. Vista grep inventory (§0) should be re-run after notices are removed.*
