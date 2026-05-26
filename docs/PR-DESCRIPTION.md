# Pull request: `checklist/v3` → `main`

**Branch:** `checklist/v3`  
**Suggested GitHub title:** Vista checklist v3 — auth, account, listings, inspections, leads, moderation, ads, agent marketing, Dream AI (Claude Haiku), and Flyway through V37

Replace the title above if your GitHub PR title differs. Paste everything from **Summary** downward into the PR description body.

---

## Summary

This pull request ships the **DreamHomes Vista / checklist backlog** on Haven in one coordinated slice: new Flyway migrations **V31–V37**, REST APIs for gaps called out in [`docs/haven-backend-gaps-and-integration.md`](haven-backend-gaps-and-integration.md), and **Vista-facing documentation** so the frontend team can re-export OpenAPI and track contract drift.

In plain terms: owners and agents get richer listing and property data (coordinates, pets/utilities, virtual tour, floor plan, ordered video URLs, price negotiable, public bio). Applicants can **flag comments** and **submit listing leads** with owner reveal and admin moderation views. Auth gains **optional HttpOnly JWT cookies**, **forgot/reset password** (tokens in DB; email sending not wired), and **account soft-delete** plus notification preferences and avatar upload. Inspections gain owner/agent lifecycle actions, **agent reschedule** and **agent extras** on approved requests. New verticals include **platform settings**, **ad campaigns** (sponsor + admin), **Dream AI** natural-language listing discovery (**Claude 3.5 Haiku** via Anthropic when `HAVEN_ANTHROPIC_API_KEY` is set; **location-substring stub** when unset), and **agent marketing gallery** with storage parity (local + R2). **Assigned ACCEPTED agents** can negotiate offers and patch listing marketing fields (not price/status), and create inspection slots where the spec allows.

---

## What landed (by commit)

| Commit | What it does |
| --- | --- |
| `76357f4` | **Vista integration docs** under [`docs/vista/`](vista/README.md): integration log, OpenAPI diff 1.0.1→1.0.2 pointer, cross-links to the canonical gap inventory. |
| `8ade7aa` | **Tests and harness updates** — new/extended coverage for leads, comments, listings, properties, offers, inspections, auth reset + JWT cookie, listing videos, agent marketing E2E, `MeController`, user profile/account services, admin listing paths; shared **`JwtCookieTestStubConfiguration`** for `@WebMvcTest`; Mockito/JDK argLine tweak in `pom.xml` where needed. |
| `4710ac3` | **Main application and schema work** — all feature code and Flyway **V31–V37** aligned with the Vista gap matrix (see [`docs/vista/integration-log.md`](vista/integration-log.md) changelog for route-level detail). |

---

## Database (Flyway V31–V37)

| Version | Purpose (short) |
| --- | --- |
| **V31** | `users.public_bio` — owner-facing copy surfaced on profiles and listings. |
| **V32** | Listings: `virtual_tour_url`, `price_negotiable`. |
| **V33** | Properties: `latitude`, `longitude` (WGS-84). |
| **V34** | Batch: password reset tokens; user notification prefs JSON, soft-delete timestamp, profile image URL; inspection statuses **NO_SHOW** / **COMPLETED**; listing **pets_allowed** / **utilities_note**; **comment_flags**; **platform_settings** singleton; **ad_campaigns**. |
| **V35** | **listing_leads** (PII + reveal flow) and **agent_marketing_media**. |
| **V36** | Unique `(listing_id, applicant_user_id)` on listing leads. |
| **V37** | Listing **floor_plan_url**, **listing_videos** table, Lagos centroid backfill for properties with null coordinates, inspection **agent_extras**. |

---

## API and behaviour highlights

**Auth and session**

- Optional **JWT cookie** (`haven.auth.jwt-cookie.*`): login can set `HttpOnly` + `SameSite`; `JwtAuthenticationFilter` accepts **Bearer first**, then cookie.
- **Forgot / reset password**: rate-limited endpoints; reset clears the auth cookie when cookies are enabled. (Email delivery is still a product/ops follow-up.)
- **Account**: `DELETE /api/me` soft-delete; `PATCH /api/me` and profile endpoints extended for prefs, avatar URL, bio, etc., as implemented.

**Listings and property**

- Richer listing create/update/response: virtual tour, negotiable, pets/utilities, floor plan, **ordered listing videos** (URL-based, similar discipline to photos).
- **Property** `PATCH` for owners/admins; create/read paths include lat/lng where applicable.
- **Owner public bio** on user + listing surfaces via repository projection.

**Leads**

- Applicant **POST** lead; owner **list** (paginated) + **reveal**; admin **GET** leads with full contact for moderation; notification kind for lead submitted.

**Inspections**

- Owner approve/decline/mark no-show; agent complete; slot creation for **owner or assigned agent**.
- **Approved** inspections: assigned agent **POST …/agent/reschedule** (same listing, conflict checks) and **PATCH …/agent/extras** for meeting notes (`agentExtras` on API responses).

**Comments and admin**

- Authenticated **POST …/comments/{commentId}/flag**; admin queue **GET** + resolve/dismiss.
- Admin listing catalogue, moderation snapshot (listing + property + audit snippet), lead views as in service layer.

**Offers**

- Repository/service changes so the **assigned ACCEPTED agent** participates in counter/negotiation paths like owner/applicant where the code allows.

**Ads, platform, Dream AI**

- Sponsor **ad campaigns** (`/api/me/ad-campaigns` + admin patch).
- **Platform settings** admin GET/PATCH (JSON merge).
- **Dream AI** — `POST /api/dream-ai/suggestions` (**JSON** `DreamAiRunTurnResponse` with **`AssistantTurnV1`**, `traceId`, `listingIds`); `POST /api/dream-ai/turns/stream` (**SSE**: `trace` / `delta` / `final` / `problem`); persisted threads (**Flyway V40** JSONB + `client_message_id`); **idempotency**; **clarify** / **compare** / ranking; **`inventoryEmpty`** vs **`queryTooStrict`** in `turn.meta`; **Dream AI rate limit** (429 Problem+JSON); **moderation** (422 / SSE `problem`); thread read **LIVE rehydration**. Configure **`HAVEN_ANTHROPIC_API_KEY`** for Haiku over a bounded LIVE catalogue; **stub** without key. See **`docs/dream-ai-capabilities.md`** and **`GET /v3/api-docs`** (Dream AI tag).

**Agent marketing**

- CRUD + reorder for agent gallery; public profile includes gallery; image MIME/size limits via config; local + R2 storage beans for avatar and agent marketing assets.

---

## Documentation for Vista

- **[`docs/vista/README.md`](vista/README.md)** — index of Vista-facing artifacts in this repo.
- **[`docs/vista/integration-log.md`](vista/integration-log.md)** — changelog + **baseline matrix** (`done` / `partial` / `todo`) vs the gap doc; **primary handoff** for “what changed for Vista.”
- **[`docs/vista/openapi-diff-1.0.1-to-1.0.2.md`](vista/openapi-diff-1.0.1-to-1.0.2.md)** — contract drift notes between frozen bundles.
- Canonical gap inventory remains **[`docs/haven-backend-gaps-and-integration.md`](haven-backend-gaps-and-integration.md)**.

**Note for reviewers:** New or changed routes should carry **springdoc** annotations so `GET /v3/api-docs` (and Scalar) stay the single contract Vista diffs against.

---

## Testing

- **Unit / slice:** broad updates across `*Test` classes; new focused tests include **`ListingVideoServiceTest`**, **`ListingVideoControllerTest`**, **`JwtCookieServiceTest`**, **`AuthControllerResetPasswordTest`**, **`ListingServiceOwnerPublicBioTest`**, extended **inspection** service/controller tests, **property** update tests, and refreshed listing/offer/user/admin tests for new fields and authorisation.
- **Integration:** e.g. **`ListingLeadFlowEndToEndIT`**, **`AgentMarketingFlowEndToEndIT`**, **`ListingMapsAndMediaIT`** (browse/detail coords + virtual tour / floor plan / videos), **`PublicUserProfileIT`** updates, **`DreamAiChatFlowIT`** (Dream AI persistence + `clientMessageId` replay), plus existing **`AbstractPostgresIT`** patterns.
- **Dream AI slice tests:** **`DreamAiControllerTest`**, **`DreamAiTurnStreamControllerTest`** (SSE event order, moderation `problem` event, markdown chunking).

Before merge:

```bash
mvn test      # Surefire
mvn verify    # Surefire + Failsafe (*IT); Docker required for Testcontainers
```

---

## Reviewer notes and known follow-ups

- **Password reset:** tokens persist correctly; **outbound email** is not implemented in this PR — document or ticket separately if Vista expects a magic link in inbox.
- **Dream AI:** ranks **only** ids from the in-memory catalogue slice (no full-DB semantic search). Configure **`HAVEN_ANTHROPIC_API_KEY`** for Haiku; miskeys or upstream errors return **502**. **SSE** delivers MVP chunked markdown + terminal `final` (not Anthropic token streaming). Contract: **`GET /v3/api-docs`** + **`docs/dream-ai-capabilities.md`**.
- **Optional JWT cookie:** disabled by default in typical envs; confirm `application.yml` / deployment env for staging cookie domain and HTTPS.
- **Vista:** after merge, regenerate or merge **`/v3/api-docs`** into the Vista frozen YAML when the team bumps the bundle version (see integration log).

---

## Pre-merge checklist

- [ ] Flyway applies cleanly from **V1** through **V37** on a fresh database.
- [ ] `mvn test` and `mvn verify` pass locally (CI parity).
- [ ] No secrets in commits; only sample values in docs and `application.yml` comments.
- [ ] Vista / PM aware of **Dream AI scope** (catalogue-bound ranking vs stub) and **email** gap above.

---

## Related files (quick navigation)

| Area | Main entry points |
| --- | --- |
| Vista handoff | [`docs/vista/integration-log.md`](vista/integration-log.md) |
| Migrations | [`src/main/resources/db/migration/`](../src/main/resources/db/migration/) `V31`–`V37` |
| Cookie + reset | `JwtCookieService`, `AuthController`, `PasswordResetService`, `SecurityConfig` |
| Leads | `lead/` package, `ListingLeadFlowEndToEndIT` |
| Listing video | `ListingVideo*`, `photo/` |
| Inspections agent | `InspectionService`, `InspectionController`, DTOs `AgentRescheduleSlotRequest`, `AgentExtrasUpdateRequest` |
| Comment flags | `CommentFlag*`, `AdminCommentFlagController` |
| Ads / platform / Dream AI | `ad/`, `platform/`, `dreamai/`, `docs/dream-ai-capabilities.md` |