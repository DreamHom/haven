# Vista ↔ Haven integration log

Living document for **closing gaps** described in the **[Vista-facing gap inventory](../haven-backend-gaps-and-integration.md)** (**canonical prose in this Haven repo**; the Vista frontend may keep a synced sibling under `vista/docs/`).

Each Haven change that Vista should consume gets a **changelog** row and an update to the **matrix** below.

## How Vista should use this

| Artifact | Where | Notes |
| --- | --- | --- |
| **Runtime OpenAPI** | `GET /v3/api-docs` on the Haven deployment | springdoc; paths in the document are **relative to `/api`** (see `OpenApiConfig` — server URL includes `/api`). |
| **Interactive docs** | `GET /scalar.html` | Same contract as `/v3/api-docs`. |
| **Bundled YAML in Vista** | `vista/docs/haven-api-docs-1.0.2.yaml` | Regenerate or hand-merge from `/v3/api-docs` when Haven ships; bump bundle version when Vista wants a frozen export. See [`openapi-diff-1.0.1-to-1.0.2.md`](./openapi-diff-1.0.1-to-1.0.2.md). |

**Rule for Haven contributors:** Any new or changed route must include **springdoc** annotations (`@Operation`, `@ApiResponses`, `@Schema` on DTOs as needed) so `/v3/api-docs` stays the single source of truth Vista can diff.

---

## Changelog (newest first)

| Date | Vista gaps § | Summary | Haven | OpenAPI / bundle |
| --- | --- | --- | --- | --- |
| 2026-05-24 | §5–7, §13–15 | **Promotions / featured placement:** Flyway **V42** adds `promotions`, `promotion_impressions`, and `promotion_clicks`. Public placement feeds: `GET /api/promotions/homepage-featured`, `/listing-search-top`, `/agent-directory-top`. Owner/agent request/read/metrics: `POST /api/promotions`, `GET /api/promotions/mine`, `GET /api/promotions/{id}`, `GET /api/promotions/{id}/metrics`. Admin control plane: `GET /api/admin/promotions`, approve/reject/pause/resume/revoke, per-promotion metrics, and metrics summary. Targets are DB-strict (`listing_id` or `agent_user_id` plus a CHECK constraint), public reads re-check listing/agent safety, and tracking stores anonymous or authenticated impressions/clicks. | `promotion/*`, `V42__create_promotions.sql`, `SecurityConfig`, `WebConfig`, admin audit + notification enum updates | springdoc; Vista: re-export `/v3/api-docs` |
| 2026-05-14 | §6 | **Dream AI contract v1:** Flyway **V40** — messages as **JSONB** + `client_message_id`; roles include **SYSTEM/TOOL**; `POST /api/dream-ai/suggestions` returns **`DreamAiRunTurnResponse`** (`AssistantTurnV1`, `traceId`, legacy `listingIds`). **`POST /api/dream-ai/turns/stream`** — SSE (`trace` / `delta` / `final` / `problem`). Orchestration: **clarify** / **compare** / ranking with **`inventoryEmpty`** vs **`queryTooStrict`** meta; **idempotent** replays; **Dream AI rate limit** (429 Problem+JSON + `Retry-After`); **moderation** hook (422 / SSE `problem`); thread read **rehydrates** listing ids to LIVE. **Tests:** `DreamAiControllerTest`, `DreamAiTurnStreamControllerTest`, `DreamAiChatFlowIT` (+ `DreamAiServiceTest`). **Doc:** [`docs/dream-ai-capabilities.md`](../dream-ai-capabilities.md). | `dreamai/*`, `V40__dream_ai_message_envelope.sql`, `DreamAiRateLimitFilter`, `OpenApiConfig` Dream AI tag + problem examples | springdoc; Vista: re-export `/v3/api-docs` |
| 2026-05-14 | §6, §8 | **Dream AI (Claude Haiku) + maps/media checks:** `POST /api/dream-ai/suggestions` — set **`HAVEN_ANTHROPIC_API_KEY`** to call Anthropic **Messages** with **Claude 3.5 Haiku** (`haven.dream-ai.anthropic.*`); model returns `listingIds` from a **bounded first-page LIVE catalogue** only (server re-validates ids; **502** on upstream/parse errors). **Without** the key, legacy **stub** (public browse, prompt as `location=`). **`ListingMapsAndMediaIT`** + **`scripts/verify-listings-catalog.sh`** exercise **`GET /api/listings`**, embedded **`property.latitude` / `longitude`**, **`virtualTourUrl`**, **`floorPlanUrl`**, **`GET /api/listings/{id}/videos`**. | `dreamai` Anthropic client, `DreamAiService`, `ListingMapsAndMediaIT` | springdoc on `DreamAiController`; Vista: re-export `/v3/api-docs` |
| 2026-05-14 | §8, §11, §4.2 | **V36 + moderation / gallery hardening:** Flyway `V36` — unique `(listing_id, applicant_user_id)` on `listing_leads`. **Leads:** owner `GET /api/listings/{id}/leads` returns **paginated** `Page` (default size 20). **Admin:** `GET /api/admin/listings/{id}/leads` — full contact fields for moderation. **`LISTING_LEAD_SUBMITTED`** payload documented on enum (`listingId`, `leadId`). **Agent gallery:** JPEG/PNG/WebP/GIF + max size via **`haven.photos.agent-marketing.max-bytes`** (default 8 MiB); **`PATCH /api/me/agent-marketing/order`** reorders items. | `V36__...sql`, `ListingLead*` paging, `AdminListingService#listingLeads`, `AgentMarketingMediaService` | springdoc on new/changed routes |
| 2026-05-14 | §3, §4.2, §8, §10, §11 | **V35 + Vista parity batch:** Flyway `V35` — `listing_leads` (PII + `revealed_at`), `agent_marketing_media`. **Auth:** optional httpOnly JWT cookie (`haven.auth.jwt-cookie.*`); filter reads Bearer first, else cookie; login sets cookie when enabled. **Leads:** `POST /api/listings/{id}/leads` (**APPLICANT**); `GET` + `POST .../leads/{leadId}/reveal` (**OWNER**); `LISTING_LEAD_SUBMITTED` notification. **Agent gallery:** `GET/POST/DELETE /api/me/agent-marketing` (multipart; **AGENT**); public profile includes `agentMarketingGallery`. **Admin:** `GET /api/admin/listings/{id}/moderation-snapshot` (listing + full property + photo count + last takedown audit snippet). **Offers:** assigned **ACCEPTED** agent may negotiate (counter / PATCH) like owner/applicant. | `V35__...sql`, `lead`, `agentmarketing`, `JwtCookieService`, `AdminListingService#moderationSnapshot`, `OfferService`/`OfferRepository`, `PublicUserProfile` | springdoc on new routes; Vista: re-export `/v3/api-docs` |
| 2026-05-14 | §5–7, §6, §4.2 | **REST for V34 tables + avatar + Dream AI stub:** `POST /api/listings/{listingId}/comments/{commentId}/flag` (auth); admin `GET /api/admin/comment-flags?status=&page=&size`, `POST .../comment-flags/{id}/resolve`, `POST .../dismiss`. **Platform:** `GET` + `PATCH /api/admin/platform-settings` (shallow JSON merge). **Ads:** `POST/GET/PATCH /api/me/ad-campaigns` (+ `/{id}` sponsor patch; submit `DRAFT`→`PENDING_REVIEW`); admin `GET/PATCH /api/admin/ad-campaigns/{id}`. **Avatar:** `POST /api/me/avatar` multipart → `haven.photos.storage` (`AvatarPhotoStorage` / R2 keys `avatars/{userId}/…`). **Dream AI:** `POST /api/dream-ai/suggestions` — stub using public browse `location` filter. | New packages `comment` flags, `platform`, `ad`, `dreamai`, `photo.storage` avatar beans, controllers | springdoc on new routes; Vista: re-export `/v3/api-docs` |
| 2026-05-14 | §3, §8, §9, §12 | **V34 batch:** Flyway `V34` — password reset tokens; `users.notification_preferences`, `account_deleted_at`, `profile_image_url`; inspection `NO_SHOW` / `COMPLETED`; listing `pets_allowed` / `utilities_note`; `comment_flags`, `platform_settings`, `ad_campaigns` tables. **Auth:** `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` (rate-limited; optional `haven.auth.debug-return-reset-token` for ITs). **Account:** soft-delete `DELETE /api/me`; `PATCH /api/me` adds `profileImageUrl`, `notificationPreferences` (JSON string); active-user email checks exclude deleted rows. **Listings:** `petsAllowed` / `utilitiesNote` on create/update/response; **OWNER** or **assigned ACCEPTED AGENT** may `PATCH /api/listings/{id}` (agents cannot change price/status). **Inspections:** owner `POST .../owner/approve` & `.../owner/decline`; agent `.../agent/complete`; owner or agent `.../mark-no-show`. **Slots:** **OWNER** or assigned **AGENT** may `POST /api/listings/{id}/slots`. **Admin:** `GET /api/admin/listings?status=` paginated catalogue. | `V34__...sql`, auth/passwordreset package, `ListingService`, `InspectionService`, `AdminListingService#catalog`, DTOs | springdoc on new routes; Vista: re-export `/v3/api-docs` |
| 2026-05-14 | §8 | **Property partial update:** `PATCH /api/properties/{id}` for **OWNER** or **ADMIN** — address, bedrooms, bathrooms, `sizeSqm`, description, lat/lng (pair). Type immutable; non-owners get `404`. | `UpdatePropertyRequest`, `PropertyService#update`, `PropertyController` | springdoc `@Operation` on PATCH |
| 2026-05-14 | — | **CI / local JDK:** Surefire + Failsafe `argLine` adds `-XX:+EnableDynamicAgentLoading` so Mockito inline attaches on newer JDKs (e.g. 25). | `pom.xml` | n/a |
| 2026-05-14 | §8, §12 | **Public owner bio:** `publicBio` on `users` — returned on `GET /api/users/{id}/profile`, included in `GET/PATCH /api/me/profile` and `PATCH /api/me` (optional field, max 4000 chars; blank clears). | Flyway `V31`, `User`, DTOs, `UserAccountService`, `UserProfileService`, `MeController` | springdoc via DTO/controller |
| 2026-05-14 | §8 | **Listing richness:** `virtualTourUrl` (optional URL, max 2048) and `priceNegotiable` (boolean, default false) on listings — on `ListingResponse`, `POST/PATCH /api/listings` bodies. | Flyway `V32`, `Listing`, commands, `ListingService`, `ListingMapper` | springdoc via DTOs |
| 2026-05-14 | §8 | **Map coordinates:** optional `latitude` / `longitude` on **property create** and on `PropertyResponse` / embedded `PropertySummary` in listing browse/detail. WGS-84 decimals; nullable for legacy rows. | Flyway `V33`, `Property`, `CreateProperty*`, `PropertyService`, controller mapping | springdoc via DTOs |
| 2026-05-14 | §3 | **Login shell / redirect:** documented on `GET /api/me` that Vista may call with the stored JWT on `/login` — `200` means still signed in, `401` means show the form. | `MeController` OpenAPI text only | springdoc |

---

## Baseline matrix (Haven `main` vs Vista gap doc)

**Legend:** `done` = supported in Haven today · `partial` = some support / different shape · `todo` = not implemented · `n/a` = product/Vista-only or out of backend scope for this repo.

| § / theme | Gap (short) | Haven status | Notes for Vista |
| --- | --- | --- | --- |
| 3 | httpOnly cookie / BFF session | `partial` | Optional **`haven.auth.jwt-cookie.enabled`** — login sets httpOnly cookie; **`JwtAuthenticationFilter`** accepts Bearer or cookie. |
| 3 | Forgot / reset password | `done` | `POST /api/auth/forgot-password` (202 + anti-enumeration), `POST /api/auth/reset-password` (204). Email delivery not wired. |
| 3 | Redirect if already signed in on `/login` | `partial` | Use **`GET /api/me`** with stored JWT — `200` = session valid (see changelog). |
| 4.2 | Applicant profile photo | `done` | **`POST /api/me/avatar`** (multipart) + **`profileImageUrl`** on `PATCH /api/me` / profile reads. |
| 4.2 | Owner avatar upload | `done` | Same as profile photo — **`POST /api/me/avatar`** writes `profile_image_url` via R2 when `haven.photos.storage=r2`. |
| 4.2 | Agent marketing media | `done` | **`GET/POST/DELETE /api/me/agent-marketing`**, **`PATCH .../order`**, MIME + max-bytes validation; **`agentMarketingGallery`** on public profile. |
| 4.2 | Virtual tour / extra listing media | `partial` | **`virtualTourUrl`** on listing create/update/response (not separate media upload). |
| 5–7 | Admin platform / ads / prefs in `localStorage` | `partial` | **REST:** `GET/PATCH /api/admin/platform-settings`; sponsor **`/api/me/ad-campaigns`**; admin **`/api/admin/ad-campaigns`**. **Featured placement:** **`/api/promotions`** public feeds, owner/agent requests, admin approvals, and metrics. **Prefs:** `notification_preferences` + `PATCH /api/me`. |
| 6 | Dream AI | `partial` | **`POST /api/dream-ai/suggestions`** — typed **`AssistantTurnV1`** + threads + idempotency + clarify/compare + meta flags. **`POST /api/dream-ai/turns/stream`** — SSE MVP (chunked markdown + terminal `final`). **Haven rate limit** + moderation. **Not** full-catalog RAG, token streaming from provider, or TOOL-row traces — see [`docs/dream-ai-capabilities.md`](../dream-ai-capabilities.md). |
| 7 | Public browse reliability | `partial` | **`GET /api/listings`** public; verify proxy/base URL if empty. |
| 8 | Real coordinates (lat/lng) | `partial` | **Create** + **`PATCH /api/properties/{id}`** + embedded **`PropertySummary`**; WGS-84 pair rule unchanged. |
| 8 | Pet rules & utilities | `done` | **`petsAllowed`**, **`utilitiesNote`** on listing create/update/`ListingResponse`. |
| 8 | Marketing description (distinct field) | `partial` | Listing `title` / `description` / `headline` / `handoverDate` already exist. |
| 8 | **Price negotiable** | `done` | `priceNegotiable` on listing. |
| 8 | Agent `PATCH` listings | `done` | Assigned **ACCEPTED** agent may PATCH marketing fields; **not** price or status (`403`). |
| 8 | Agent slot creation | `done` | `POST /api/listings/{id}/slots` for **OWNER** or assigned **AGENT**. |
| 8 | Admin listing catalog | `done` | `GET /api/admin/listings?status=&page=&size=` |
| 8 | Comment moderation (user flag + admin queue) | `done` | **`POST /api/listings/{id}/comments/{commentId}/flag`**; admin **`/api/admin/comment-flags`** + resolve/dismiss. |
| 8 | TAKEN_DOWN admin detail snapshot | `done` | **`GET /api/admin/listings/{id}/moderation-snapshot`** — listing + **`PropertyResponse`** + photo count + last **`LISTING_TAKEDOWN`** audit snippet (reason from metadata JSON). |
| 9 | Owner approve / decline / no-show | `done` | `POST /api/inspections/{id}/owner/approve`, `/owner/decline`, `/mark-no-show` (owner or assigned agent on no-show). |
| 9 | Agent inspection decisions | `partial` | Agent **`/agent/complete`**; broader agent decline paths still Vista-only if needed. |
| 9 | Applicant claim slot | `done` | `POST /api/inspections` |
| 10 | Agent-side offer mutations | `done` | Assigned **ACCEPTED** agent visible in negotiation; **`PATCH /api/offers/{id}`**, **`POST .../counter`** allow **AGENT**. |
| 11 | Owner lead contact reveal | `done` | **Leads** + reveal; **paginated** owner list; **admin** `GET /api/admin/listings/{id}/leads` with full contact; DB unique on `(listing, applicant)`. |
| 12 | Notification preferences | `done` | **`notificationPreferences`** JSON string on user; read/write via **`GET /api/me/profile`** and **`PATCH /api/me`**. |
| 12 | Account deletion | `done` | **`DELETE /api/me`** — soft delete, email anonymised, sessions revoked. |
| 12 | Public owner bio richness | `partial` | **`publicBio`** on user public + private profile; owner (and other roles) edit via **`PATCH /api/me`**. |
| 13–15 | Admin queues, ads, delete, etc. | `partial` | **Comment flags** admin queue + resolve/dismiss; **platform settings**; **ad campaigns** user + admin; **promotions** delivery/reporting for featured listings/agents; listing catalogue already done. Billing/payment remains open. |

---

## Suggested implementation order (from Vista §18)

1. Operational reliability + confirm Vista proxy → `GET /api/listings` / public detail.  
2. Password reset (+ rate limits).  
3. Listing geometry & richness (coordinates; pets/utilities **done** in V34).  
4. Inspections state machine.  
5. Leads & PII gates.  
6. Profiles & uploads (avatar **done**).  
7. Settings persistence.  
8. Account deletion.  
9. Promotions / ads commerce: delivery and basic reporting exist for featured placements; payment/billing remains.
10. Dream AI backend.

---

## Cross-links

- Haven architecture: [`../TRADEOFFS.md`](../TRADEOFFS.md), [`../STATE-OF-THE-SYSTEM.md`](../STATE-OF-THE-SYSTEM.md)
- **OpenAPI bundle drift (1.0.1 → 1.0.2):** [`openapi-diff-1.0.1-to-1.0.2.md`](./openapi-diff-1.0.1-to-1.0.2.md)

## Remaining vs Vista gap inventory

The prose inventory lives at **[`docs/haven-backend-gaps-and-integration.md`](../haven-backend-gaps-and-integration.md)** (including **Appendix A: route parity matrix**). The table below is the **Haven implementation** rollup; cross-check that file’s §2 banners and §3–§15 tables when Vista copy or `PublicApiNotice` / `PrototypeNotice` text drifts.

| § / theme | Still open on Haven (matrix = `partial`) |
| --- | --- |
| 3 | Cookie/BFF: optional cookie exists; full BFF/session product shape may still differ from Vista. |
| 3 | “Already signed in” on `/login`: contract is **`GET /api/me`** — no dedicated redirect endpoint. |
| 4.2 | Extra listing media beyond **`virtualTourUrl`** (no separate listing gallery product). |
| 5–7 | Platform/ads prefs: REST exists; promotions expose featured placement feeds and metrics; Vista may still mirror more `localStorage` keys or UX. |
| 6 | Dream AI: **no API key** → stub only; **with key** → **Haiku** bounded slice; **JSON + SSE** contracts, idempotency, rate limit, moderation, thread rehydrate per [`docs/dream-ai-capabilities.md`](../dream-ai-capabilities.md). Still **not** full-DB semantic search or provider token streaming. |
| 7 | Public browse “reliability”: API is public; empty responses/env/proxy are operational/Vista concerns. |
| 8 | Coordinates: supported on create/patch/summary; legacy/null rows and FE validation remain. |
| 8 | “Marketing description” as a **distinct** fourth text field vs `title` / `description` / `headline`. |
| 9 | Agent inspection: **`/agent/complete`**; broader agent-driven decline/cancel paths if Vista expects them. |
| 12 | Public bio: field exists; richer owner “story” modules (if any) are Vista/product. |
| 13–15 | Admin/ads/delete breadth: comment flags + platform + ad campaigns + promotion delivery/reporting done; billing/payment and other Vista admin stories may extend beyond Haven. |

**`todo` in matrix:** none at last edit — if Vista’s canonical doc adds new rows, mirror them here after re-reading that file.
