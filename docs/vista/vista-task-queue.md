# Vista Task Queue

Master list of frontend work to be done against Haven. Each entry follows the template in `cursor-handoff-prompt.md`.

**Read `cursor-handoff-prompt.md` first.**

Status: ⏳ BACKEND IN PROGRESS · ✅ READY FOR VISTA · 🚧 IN PROGRESS · ✅ DONE · ⏸️ PAUSED · ❌ CANCELLED

---

## Recent backend changes that don't need Vista work

These shipped on the backend but do not change any API contract Vista consumes — listed here so Vista knows the underlying behaviour evolved.

- **Item 25 (Dream AI provider abstraction)** — Dream AI's Anthropic + OpenAI integrations refactored into a Service → Provider pattern. Internal refactor only; existing endpoints + response shapes unchanged. Two new optional fields on `meta`: `llmProvider` and `embeddingProvider`, populated with the active provider's name (e.g. `"anthropic"`, `"openai"`) when each was called — null when the relevant subsystem wasn't used (e.g. on the FAST rankMode path `llmProvider` is null; on the stub/substring path both are null). Vista can surface these as debug indicators if useful, otherwise ignore. Provider selection is controlled by two new Railway env vars: `HAVEN_DREAM_AI_LLM_PROVIDER` (default `anthropic`) and `HAVEN_DREAM_AI_EMBEDDING_PROVIDER` (default `openai`). Three scaffolded values per side (`openai` / `gemini` for LLM; `voyage` / `self-hosted` for embedding) exist as stubs that throw `UnsupportedOperationException` until v2 fills them in — selecting them in production would 502 the Dream AI surface. See `docs/dream-ai-providers.md` for the full swap matrix. **No Vista impact required** — defaults match v1 behaviour exactly.
- **Item 1 cache-eviction test fix** — `DatabaseCleanupTestExecutionListener` now evicts the Caffeine cache between IT tests, and `ListingService.create()` + `PropertyService.update()` now carry `@CacheEvict` for the listings:browse + listings:detail namespaces (gaps the original Item 1 work left). Fixes test-isolation flakes in `ListingFlowEndToEndIT`, `ListingMapsAndMediaIT`, `ListingTrustSignalsIT` and also corrects a real production bug — creating a new listing or editing a property no longer leaves stale browse cache visible to subsequent reads for up to 60s. **No Vista impact** — internal test infra + cache-correctness fix; no API contract or response-shape change.
- **Item 24 (OpenAI embeddings now wired)** — `application.yml` previously had no `haven.dream-ai.embeddings.*` section, so `HAVEN_OPENAI_API_KEY` env var on Railway never bound to `ListingEmbeddingProperties`. The whole pgvector NN candidate-selection path was dead code. The YAML now binds all six env vars (`HAVEN_OPENAI_API_KEY`, `HAVEN_OPENAI_EMBEDDING_MODEL`, `HAVEN_OPENAI_EMBEDDING_DIMENSIONS`, `HAVEN_OPENAI_BASE_URL`, `HAVEN_OPENAI_CONNECT_TIMEOUT_MS`, `HAVEN_OPENAI_READ_TIMEOUT_MS`). `.env.example` updated to match. **No Vista impact** — Dream AI search just becomes semantically smarter when the key is set on the deploy (Dream AI's response shape stays `{listingIds: [...]}`).
- **Item 17 (Dream AI catalogue now carries verification fields)** — every listing row that goes to Claude for ranking + compare now includes `ownerVerified` (boolean, derived from `User.identityVerifiedAt`) and `propertyDocumentsVerified` (boolean, derived from `Property.documentsVerifiedAt`). Search + compare system prompts updated so Claude honours "verified owners" / "verified property" constraints in the user's natural-language prompt. **No Vista impact** — invisible to the frontend; only affects ranking quality when the user explicitly asks for "verified" in the prompt.
- **Item 22 (Dream AI embedding-distance threshold)** — pgvector NN query now enforces a cosine-distance cutoff (`haven.dream-ai.embeddings.max-distance`, default 0.5, override via `HAVEN_DREAM_AI_EMBEDDING_MAX_DISTANCE`). Junk prompts ("purple elephant tap dance") produce zero candidates and the orchestrator early-bails with `kind=no_results` + `meta.queryTooStrict=true` WITHOUT calling Claude. **No Vista impact** — internal behaviour change; same `no_results` shape, no contract diff. Vista only sees Anthropic bills drop on adversarial traffic.
- **Item 23 default behaviour (Dream AI rankMode auto-defaults)** — the orchestrator now picks a sensible `rankMode` per caller: anonymous → FAST (cost defence, skips Claude, returns pgvector NN order), authenticated → SMART (existing behaviour). **No Vista impact for default callers** — same response shape. Vista only needs to add the explicit `rankMode` field to the request if it wants to override the default (e.g. "smart search" button for anonymous users). The FAST-mode mode-indicator UI is covered by VTASK-016.
- **Item 12 (one LIVE listing per (property, listing_type) enforced)** — `POST /api/listings` and `PATCH /api/listings/{id}` (specifically status-flip-to-LIVE) can now return `409 Conflict` with a new ProblemDetail `type` URI suffix `listing.duplicate-open-listing-for-property-and-type`. A property may carry at most one LIVE `RENT` and at most one LIVE `SALE` listing at any moment (the two coexist; a second LIVE of the same type is rejected). Enforced by both a service-level pre-check and a Postgres partial unique index (V47 migration). Vista doesn't need a structural change, but the create-listing form should surface a friendly error on this specific 409. Suggested UI copy when the response carries that `type` suffix: **"This property already has an active rent listing — close that one first."** (substitute "rent" with the relevant `listing_type` from the failed request). Existing generic-409 handling continues to work; this is a copy improvement opportunity, not a hard requirement.

---

## VTASK-001 — Listing trust signals (Possible Scam + Verified)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 16
**Backend status:** ✅ shipped (uncommitted on branch `lukasio` — see Haven repo working tree)

### Why this matters

Ngozi-the-skeptic reads trust signals before deciding to engage with any owner. Today every listing looks the same — no warning on unverified owners, no badge on verified properties. The two signals are critical for the marketplace's anti-fraud story.

The backend now embeds both signals on every listing payload (browse, detail, mine, create-response, update-response) so Vista can render the chips with zero follow-up fetches — no N+1 against `/users/{id}/profile` per card.

### API contract

**Endpoints affected (all already exist; only the response shape changed):**

- `GET /api/listings` — paginated browse feed (public)
- `GET /api/listings/{id}` — listing detail (public)
- `GET /api/listings/mine` — owner's listings
- `POST /api/listings`, `PATCH /api/listings/{id}` — create / update responses

**Two new / annotated fields on every `ListingResponse`:**

| Field | Type | Semantics |
|---|---|---|
| `ownerIdentityVerifiedAt` | `string` (ISO-8601 Instant) or `null` | Owner's identity-verification timestamp. **Null = owner not verified → render warning chip.** Non-null = owner verified → no chip. |
| `property.documentsVerifiedAt` | `string` (ISO-8601 Instant) or `null` | Property-document verification timestamp. **Non-null = render green "Verified" badge.** Null = no badge. *(Field already existed; the `@Schema` description is new and explicit.)* |

**Three-state trust matrix:**

| `ownerIdentityVerifiedAt` | `property.documentsVerifiedAt` | UI signal |
|---|---|---|
| `null` | (any) | ⚠️ **Possible Scam** warning chip |
| non-null | `null` | no chip (baseline) |
| non-null | non-null | ✓ **Verified** badge |

Both chips CAN co-exist on the rare card where the owner is unverified but the property docs got verified separately — render both.

**Concrete JSON examples:**

State 1 — unverified owner (warning chip):
```json
{
  "id": 17,
  "propertyId": 42,
  "ownerId": 7,
  "ownerIdentityVerifiedAt": null,
  "title": "3-bed apartment, Lekki",
  "status": "LIVE",
  "property": {
    "id": 42,
    "type": "APARTMENT",
    "address": "12 Lekki Phase 1, Lagos",
    "bedrooms": 3,
    "bathrooms": 2,
    "documentsVerifiedAt": null
  }
}
```

State 2 — owner verified, property docs not yet (no chip):
```json
{
  "id": 18,
  "ownerIdentityVerifiedAt": "2026-04-12T10:00:00Z",
  "property": {
    "id": 43,
    "documentsVerifiedAt": null
  }
}
```

State 3 — both verified (green badge):
```json
{
  "id": 19,
  "ownerIdentityVerifiedAt": "2026-04-12T10:00:00Z",
  "property": {
    "id": 44,
    "documentsVerifiedAt": "2026-04-18T14:30:00Z"
  }
}
```

(Rare) State 4 — unverified owner + verified property docs (both chips render):
```json
{
  "id": 20,
  "ownerIdentityVerifiedAt": null,
  "property": { "id": 45, "documentsVerifiedAt": "2026-04-20T09:00:00Z" }
}
```

**Error responses:** none specific to this change. Existing 404 / 401 / 403 apply (see existing OpenAPI for the endpoint).

### Vista implementation notes

**Files likely to touch:**

- `components/listings/ListingCard.tsx` (or equivalent) — render trust chips alongside the existing price + headline
- `components/listings/ListingDetail.tsx` (or equivalent) — same chip placement, larger size
- `components/trust/TrustChips.tsx` — NEW component encapsulating the matrix logic; props: `{ ownerIdentityVerifiedAt: string | null; documentsVerifiedAt: string | null }`; renders 0, 1, or 2 chips per the matrix above
- TypeScript model: extend `ListingResponse` and `PropertySummary` types — `ownerIdentityVerifiedAt: string | null` on the listing; `documentsVerifiedAt: string | null` on the embedded property (already present, just confirm typed correctly)

**Where to render:**

1. **Browse card view** — chips appear in the card footer or as a row immediately under the listing title. Keep them visually compact (small pill / chip components) so they don't dominate a card.
2. **Listing detail view** — same chip(s) at the top of the trust / verification block, larger and with a tooltip-style hover that explains the meaning.

**Copy suggestions** (lifted from post-session-tasks.md Item 16):

- Warning chip: `⚠️ Possible Scam — this owner hasn't completed identity verification. Be cautious before sending money or signing.`
- Verified badge: `✓ Verified — property documents have been confirmed by our admins.`

**Tooltip extension** (on hover / focus, especially on mobile-tap): show the long copy. Default chip text on the card is shorter — e.g. just `⚠️ Possible Scam` and `✓ Verified` — to keep cards scannable.

**Accessibility:** chip + tooltip should both have `aria-label` carrying the long copy so screen readers announce the full meaning, not just the icon.

### Test plan (Vista side)

**Manual scenarios against `haven.dreamhomes.today`:**

1. `GET https://haven.dreamhomes.today/api/listings` — confirm at least one listing in the paginated response has `ownerIdentityVerifiedAt: null` (unverified-owner case for warning chip rendering).
2. `GET https://haven.dreamhomes.today/api/listings/{id}` — same field appears on the detail response.
3. Browse the listings page in Vista — verify the warning chip renders on unverified-owner listings, not on verified-owner listings.
4. Open a listing detail page — verify the chip renders in the trust block.
5. Verify the green "Verified" badge renders on listings where `property.documentsVerifiedAt !== null`.
6. Edge case — find or seed a listing with `ownerIdentityVerifiedAt === null` AND `property.documentsVerifiedAt !== null`; confirm both chips render together.
7. Cache: refresh once, verify the chip stays. Open a private window, verify same.

**Visual states to test:**

- Loading skeleton (chips absent until response arrives — no flash of "verified" before data lands)
- Empty browse feed (no card → no chips, page-level empty state)
- Error state (failed fetch — chips don't render at all; page-level error handling)

### What NOT to do

- Don't infer "verified" from the absence of the warning. The `ownerIdentityVerifiedAt` being non-null does NOT mean the property is verified — keep the two signals independent.
- Don't render the warning chip on `ownerId === currentUserId` cards in the owner's own listing-mine view — they know they're unverified. Show an actionable banner there instead pointing at `/verifications`.
- Don't fetch `/users/{ownerId}/profile` to enrich the card. The whole point of this backend change is to avoid that N+1.

---

## VTASK-002 — Verification rejection reason exposed to submitter

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 21
**Backend status:** ✅ shipped (uncommitted on branch `lukasio` — see Haven repo working tree)

### Why this matters

Today users who get rejected see only "REJECTED" with no reason. They resubmit blindly with the same mistake. Surfacing the admin's reason closes the feedback loop — Amaka submits → admin rejects with "photo too blurry, retake in better light" → her dashboard tells her exactly that → she resubmits with a sharper photo.

### API contract

**Endpoint affected (already exists; only the response shape changed):**

- `GET /api/verifications/mine` — paginated list of caller's own verification submissions

**New field on every `VerificationResponse`:**

| Field | Type | Semantics |
|---|---|---|
| `decisionReason` | `string` or `null` | **Only populated when `status == "REJECTED"`.** The reason the admin supplied when rejecting. Always `null` on PENDING / APPROVED rows — even if the underlying DB column happens to carry a value from a prior decision cycle, the response strips it. |

**Concrete JSON examples:**

REJECTED row (decisionReason populated):
```json
{
  "content": [
    {
      "id": 101,
      "type": "OWNER_IDENTITY",
      "status": "REJECTED",
      "submitterUserId": 50,
      "targetUserId": 50,
      "targetPropertyId": null,
      "documentRefs": "{\"kind\":\"NIN\",\"ref\":\"AB1234567\"}",
      "submittedAt": "2026-05-09T08:00:00Z",
      "decidedAt": "2026-05-10T14:23:00Z",
      "decisionReason": "Photo too blurry, retake in better light."
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

PENDING row (decisionReason always null):
```json
{
  "id": 102,
  "status": "PENDING",
  "submittedAt": "2026-05-10T08:30:00Z",
  "decidedAt": null,
  "decisionReason": null
}
```

APPROVED row (decisionReason always null):
```json
{
  "id": 103,
  "status": "APPROVED",
  "submittedAt": "2026-05-09T08:00:00Z",
  "decidedAt": "2026-05-10T14:23:00Z",
  "decisionReason": null
}
```

**Error responses:** none specific to this change. Existing 401 applies if unauthenticated.

### Vista implementation notes

**Files likely to touch:**

- `components/verifications/VerificationStatusCard.tsx` (or equivalent on the user's verifications dashboard)
- TypeScript model: extend `VerificationResponse` type — add `decisionReason: string | null`

**Rendering:**

- When `status === "REJECTED"` AND `decisionReason` is non-null: render the reason prominently — full-width banner / callout under the status pill, not a tooltip. The user should not have to hover or click to see it.
- When `status === "REJECTED"` AND `decisionReason` is null (legacy rejected rows from before this change): fall back to a generic "Your verification was rejected. Contact support if you'd like more detail."
- When `status === "PENDING"` or `"APPROVED"`: don't render anything reason-related (the field is always null on these states by API contract).

**Copy suggestions:**

```
Your verification was rejected:
"{decisionReason}"

Address this and resubmit below.
```

The quotes around `{decisionReason}` make it clear the text is the admin's words, not Haven's voice. The "Address this and resubmit" line should sit next to (or call out) the resubmit CTA.

**State management:** the `decisionReason` is part of the same fetch that loads the user's verification list — no extra API call needed.

### Test plan (Vista side)

**Manual scenarios against `haven.dreamhomes.today`:**

1. Log in as a user with at least one REJECTED verification. `GET /api/verifications/mine` and confirm `decisionReason` is a non-null string on the REJECTED row.
2. Verify Vista renders the reason text verbatim under the status pill.
3. Confirm the resubmit CTA is visible and adjacent to the reason callout.
4. Submit a fresh verification (PENDING) — confirm Vista doesn't render any reason-related UI on the new row.
5. (If able to coordinate with admin) Have an admin reject the new submission with a specific reason → refresh Vista → confirm the reason appears.

**Edge cases to poke:**

- Long reason text (admin types a paragraph) — confirm Vista wraps gracefully, doesn't truncate silently
- Reason text containing quotes / special characters — confirm no XSS, no broken rendering
- Legacy REJECTED rows where `decisionReason` is null (admin rejected before this field was exposed) — confirm fallback copy renders

**Visual states:**

- REJECTED + reason → red status pill + prominent reason callout + resubmit CTA highlighted
- REJECTED + no reason (legacy fallback) → red status pill + generic fallback message + resubmit CTA
- PENDING → yellow status pill + no reason UI
- APPROVED → green status pill + no reason UI

### What NOT to do

- Don't show the `decisionReason` field on PENDING / APPROVED rows even if you see one in the response. The API contract is that it's always null on those states, but defensive UI should respect the *intent* of "REJECTED-only" not just the payload value.
- Don't paraphrase or summarise the admin's reason. Render it verbatim in quotes. Paraphrasing risks losing actionable detail (e.g. "blurry" → "low quality" — the user might not understand to retake the photo).
- Don't render the reason as a tooltip or behind a click — the whole point of this change is to surface it loudly.

---

## VTASK-003 — UI-level inspection cancellation (post-APPROVED, with reason)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 7 Gap C
**Backend status:** ✅ shipped on branch `lukasio` (uncommitted)

### Why this matters

Today applicants and owners are locked in after an inspection is approved — emergencies become forced no-shows that damage the applicant's record. The new cancel-with-reason endpoint gives both parties a graceful exit and surfaces the reason to the other party so they understand what happened.

### API contract

**Endpoint:** `POST /api/inspections/{id}/cancel`

**Auth:** Bearer token required (any authenticated role; the service enforces the caller must be applicant, listing owner, or active assigned agent).

**Request body** (`application/json`):

```json
{
  "reason": "Work emergency, can't make it"
}
```

| Field    | Type   | Required | Validation       |
|----------|--------|----------|------------------|
| `reason` | string | yes      | NotBlank, max 200 chars |

**Success response** — `200 OK`:

```json
{
  "id": 33,
  "slotId": 12,
  "applicantId": 100,
  "status": "CANCELLED",
  "notes": "Coming with my husband.",
  "agentExtras": null,
  "createdAt": "2026-05-10T12:00:00Z",
  "updatedAt": "2026-05-12T09:00:00Z"
}
```

**Side effects:**

1. Status flips to `CANCELLED`, `cancellation_reason` persisted server-side.
2. Slot is freed (drops out of the partial UQ on `inspection_requests(slot_id) WHERE status IN ('PENDING','APPROVED')`).
3. `inspection.cancelled.v1` outbox event is written in the same transaction → Kafka relay ships → `InspectionCancelledListener` fans an `INSPECTION_CANCELLED` notification (carrying the reason) to every other party. The canceller does NOT get a self-notification.

**Error responses** — all `application/problem+json` (RFC 7807):

| Status | Type suffix       | When                                          | Vista copy suggestion |
|--------|-------------------|-----------------------------------------------|-----------------------|
| 400    | `validation-failed` | `reason` missing, blank, or > 200 chars     | "Tell us why you're cancelling (max 200 characters)." |
| 401    | `unauthenticated`   | Missing/invalid JWT                         | Redirect to login. |
| 403    | `forbidden`         | Caller is none of {applicant, listing owner, active assigned agent} | "Only the applicant, listing owner, or assigned agent can cancel this inspection." |
| 404    | `not-found`         | Inspection id doesn't exist                 | "We couldn't find that inspection — it may have been removed." |
| 409    | `conflict`          | Inspection is in a non-cancellable state (CANCELLED / DECLINED / COMPLETED / NO_SHOW) | "This inspection has already ended — there's nothing to cancel." |

ProblemDetail body shape (example for the 409):

```json
{
  "type": "https://github.com/DreamHom/haven/blob/main/docs/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Inspection request 33 is not in a cancellable state (only PENDING or APPROVED can be cancelled)"
}
```

**Backwards compatibility:** the legacy `DELETE /api/inspections/{id}` (applicant-only, PENDING-only, no reason) still works — Vista can call it where it already does. The new path is recommended for the cancel UI on inspection-detail screens because it works from APPROVED and captures the audit-grade reason.

### Vista implementation notes

**Files likely to touch:**

- `components/inspections/InspectionDetail.tsx` — add a "Cancel" CTA visible to any of the three eligible roles when status is `PENDING` or `APPROVED`.
- `components/inspections/CancelInspectionModal.tsx` — NEW. Modal asking for the reason (textarea, 200-char counter); calls the endpoint on submit.
- `lib/api/inspections.ts` — add `cancelInspection(id, reason)` wrapper that POSTs to `/api/inspections/{id}/cancel`.
- `components/notifications/NotificationItem.tsx` — render new `INSPECTION_CANCELLED` notification kind with the reason inline.

**State machine:**

```
status === 'PENDING' || status === 'APPROVED' → show Cancel button (for eligible roles)
status === 'CANCELLED' → show "Cancelled by <party>: <reason>" banner
otherwise → don't show
```

**Eligibility (frontend mirror of backend):**

- Applicant always sees the button on their own bookings while status is cancellable.
- Owner / agent see the button if they're authenticated as the listing owner or active assigned agent — these can be derived from the listing detail (owner + agent fields) Vista already loads.

**Modal copy:**

- Title: "Cancel inspection"
- Body explainer: "We'll let the other party know — please give them a brief reason."
- Field label: "Reason for cancelling"
- Placeholder: "e.g. work emergency, slot conflict, no longer interested"
- Counter: `X / 200`
- Submit button: "Cancel inspection"
- Secondary button: "Keep inspection"

**Optimistic UX:** show a spinner on the submit button; on 200, close the modal + show a toast "Inspection cancelled. The other party has been notified." On 409, close the modal + show "This inspection isn't cancellable anymore — refresh to see the latest state."

### Test plan (Vista side)

- **Happy path A — applicant cancels PENDING:** log in as applicant who booked, navigate to inspection detail, click Cancel, enter reason, submit. Verify status flips and the owner's notification tray shows the cancellation with the reason.
- **Happy path B — owner cancels APPROVED:** log in as the listing owner (Amaka), find an APPROVED inspection, cancel with reason. Verify applicant (Temi) sees the notification.
- **Happy path C — agent cancels APPROVED:** log in as the active assigned agent (Emeka), cancel. Verify both applicant + owner receive notifications.
- **400 path:** submit modal with empty reason → toast "Tell us why you're cancelling".
- **409 path:** open modal on an already-CANCELLED inspection (force the state via DevTools) → toast about the inspection not being cancellable.
- **403 path:** log in as a third-party applicant who's not on the row → button should be hidden (UI gate), but call the endpoint manually → toast about not being eligible.
- **Visual states:** loading spinner on submit, disabled state, error banner, success toast, empty reason warning.

### What NOT to do

- Don't hide the Cancel button behind "Are you sure?" without capturing the reason — the reason IS the safeguard. A one-tap cancel with a forced reason is the right shape.
- Don't render the raw `detail` from the ProblemDetail to end users. Branch on `status` + `type` and use the copy suggestions above.
- Don't call the legacy `DELETE /api/inspections/{id}` path for the new flow — only use it for the existing applicant-only PENDING cancel surface if it's already wired.
- Don't allow > 200 chars client-side (the backend will 400) — show the counter and disable submit when over.

---

## VTASK-004 — Post-APPROVED inspection action menu (docs only — endpoints already exist)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 7 Gap D
**Backend status:** ✅ shipped on branch `lukasio` (uncommitted) — rich OpenAPI annotations added to all four endpoints.

### Why this matters

Once an inspection is `APPROVED`, four actions are possible (reschedule, complete, no-show, cancel-with-reason). The endpoints exist but Vista has no UI exposing them — owners/agents can't take these actions from the app today. Surfacing them as a kebab menu on the approved inspection card closes the operational loop after a booking is confirmed.

### API contract — all four endpoints

All four return `200 OK` with the updated `InspectionResponse` body on success. Auth header `Authorization: Bearer <jwt>` required throughout. Error shapes are ProblemDetail (RFC 7807); the table below summarises status codes per endpoint.

#### 1. Reschedule to another slot (assigned agent only)

`POST /api/inspections/{id}/agent/reschedule`

Request body:

```json
{ "slotId": 60 }
```

Response (`200`):

```json
{
  "id": 33, "slotId": 60, "applicantId": 100,
  "status": "APPROVED", "notes": "Coming with my husband.",
  "agentExtras": null,
  "createdAt": "2026-05-10T12:00:00Z",
  "updatedAt": "2026-05-11T08:30:00Z"
}
```

**Validity:** `status === 'APPROVED'`. Caller must hold the `AGENT` role AND have an `ACCEPTED` `agent_listings` row on the listing.

**Errors:**

| Status | Why |
|--------|-----|
| 400 | Body fails validation (missing `slotId`) |
| 401 | Not authenticated |
| 403 | Caller is not the active assigned agent |
| 404 | Inspection id or new slot id doesn't exist |
| 409 | Not APPROVED, slot on different listing, or new slot already claimed by another active request |

#### 2. Mark completed (assigned agent only)

`POST /api/inspections/{id}/agent/complete`

No request body. Response shape: `InspectionResponse` with `status: "COMPLETED"`.

**Validity:** `status === 'APPROVED'`. Caller must be the active assigned agent.

**UX gate (recommended):** surface this action only after the slot's `endsAt` has passed. Marking completed before the booking even ends is suspicious data.

**Errors:** 401 / 403 / 404 / 409 (same semantics as above).

#### 3. Mark no-show (owner OR assigned agent)

`POST /api/inspections/{id}/mark-no-show`

No request body. Response: `InspectionResponse` with `status: "NO_SHOW"`.

**Validity:** `status === 'APPROVED'`. Caller must be either the listing owner OR the active assigned agent. Applicant CANNOT call this (that would be a cancel).

**UX gate (recommended):** surface only after slot's `startsAt` has passed.

**Errors:** 401 / 403 / 404 / 409.

#### 4. Cancel with reason (any participating party)

`POST /api/inspections/{id}/cancel` — fully documented in VTASK-003. Available from `PENDING` and `APPROVED` for the applicant, owner, or active assigned agent. Reason required.

### Vista implementation notes

**Files likely to touch:**

- `components/inspections/InspectionCard.tsx` (or a new variant for the post-APPROVED view) — show a kebab menu (⋯) when status is `APPROVED`, with up to 4 actions visible depending on caller role + time gates.
- `components/inspections/RescheduleSlotPicker.tsx` — NEW. Modal that lists other available slots on the listing and POSTs the agent reschedule.
- `components/inspections/ConfirmAction.tsx` — generic "are you sure?" modal for the two no-body actions (complete / no-show); CancelInspectionModal from VTASK-003 is the cancel variant.
- `lib/api/inspections.ts` — add `rescheduleInspection`, `completeInspection`, `markNoShow` wrappers alongside the cancel one.

**Action menu visibility matrix:**

| Role           | Cancel | Reschedule | Mark completed | Mark no-show |
|----------------|--------|------------|----------------|--------------|
| Applicant      | ✓ (any time it's PENDING/APPROVED) | ✗ | ✗ | ✗ |
| Listing owner  | ✓ (PENDING/APPROVED) | ✗ | ✗ | ✓ (APPROVED, after startsAt) |
| Active agent   | ✓ (PENDING/APPROVED) | ✓ (APPROVED) | ✓ (APPROVED, after endsAt) | ✓ (APPROVED, after startsAt) |
| Anyone else    | ✗ | ✗ | ✗ | ✗ |

**Time-gate utility:** a small helper `isAfter(slotMoment)` that compares `Date.now()` against the slot's `startsAt` / `endsAt` (the inspection response already exposes `slotId`; the slot's times are on the listing's slot list). Hide the action when the time gate fails — don't grey it out (less visual noise).

**Reschedule modal:**

- Fetch the listing's `GET /api/listings/{id}/slots` and filter to slots that are not claimed.
- Present them as a vertical list with day + time + duration; clicking one POSTs the reschedule.
- On 409 (slot taken in the meantime), refresh the list and show a banner: "That slot was just taken — pick another."

**Complete + no-show confirmations:** one-sentence body ("This will close the booking — applicant won't be able to reschedule"). Single primary CTA.

### Test plan (Vista side)

- **Reschedule (agent):** log in as Emeka, find an APPROVED inspection on a listing he's assigned to, pick a new slot, verify slot pointer updates and existing slot is freed (visible by another applicant being able to book it).
- **Complete (agent):** log in as Emeka, find an APPROVED inspection whose slot end has passed, mark completed. Verify status flips to COMPLETED.
- **No-show (owner):** log in as Amaka, find one of her APPROVED inspections whose slot start has passed, mark no-show. Verify status flips.
- **No-show (agent):** same flow as Emeka.
- **Cancel:** covered by VTASK-003.
- **Action menu rendering gates:** verify the menu shows only the actions valid for the caller's role + the current time vs. slot. E.g. applicant viewing an APPROVED inspection sees only Cancel; agent viewing an APPROVED inspection whose end has passed sees all four.
- **409 race conditions:** open the reschedule modal, manually book the target slot via another tab as a different applicant, then submit the reschedule — verify 409 → graceful re-fetch.

### What NOT to do

- Don't conflate the role gates server-side enforces (403) with the time gates Vista enforces (UI hiding). Time gates are UX hints, not security — even the agent can succeed on a "premature" complete via the API.
- Don't render the kebab menu when status isn't `APPROVED` (or `PENDING` for cancel-only) — it implies there's something to do when there isn't.
- Don't hide the cancel action when the user is the applicant — historically Vista may have only shown an applicant the legacy DELETE cancel for PENDING. The new POST /cancel works for APPROVED too — this is the whole point of Gap C.

---

## VTASK-005 — Inspection notifications surfaced in real time

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 7 Gap A + B
**Backend status:** ✅ shipped on branch `lukasio` (uncommitted)

### Why this matters

Two perception gaps closed in one push:

- **Gap A** — the active assigned agent (when set) now gets an `INSPECTION_REQUESTED` notification alongside the owner, every time an applicant books a slot on a listing they manage.
- **Gap B** — applicants now get a real-time `INSPECTION_APPROVED` or `INSPECTION_DECLINED` notification the moment the owner acts, instead of having to refresh.

Both fix the "I never heard back" reality that today makes the inspection flow feel half-built.

### API contract

No new endpoints — these are new notification kinds delivered via the existing notification surfaces. Vista should already be subscribed to:

- `GET /api/notifications/mine?unreadOnly=true&kind=INSPECTION_REQUESTED` etc. (pull)
- `GET /api/notifications/stream` (Server-Sent Events push)

**New `NotificationKind` values:**

- `INSPECTION_REQUESTED` (existing — now also fired for the assigned agent, not just the owner)
- `INSPECTION_APPROVED` (new — recipient is the applicant)
- `INSPECTION_DECLINED` (new — recipient is the applicant)

The `INSPECTION_CANCELLED` kind that ships alongside Gap C (VTASK-003) lives in the same family — recipients are every party except the canceller.

**Notification row shape** (existing `GET /api/notifications/mine` schema, no change):

```json
{
  "id": 4271,
  "kind": "INSPECTION_APPROVED",
  "createdAt": "2026-05-12T09:42:11Z",
  "readAt": null,
  "payload": {
    "eventId": "f8c7b6a5-1234-5678-9abc-def012345678",
    "inspectionRequestId": 33,
    "slotId": 12,
    "listingId": 7,
    "applicantId": 2,
    "decision": "APPROVED",
    "reason": null,
    "occurredAt": "2026-05-12T09:42:10Z"
  }
}
```

For `INSPECTION_DECLINED` the same payload shape applies; `decision` is `DECLINED` and `reason` may be a string (set if the owner supplied a justification).

For `INSPECTION_REQUESTED` payload (existing shape, unchanged) — note the agent and owner now receive identically-shaped rows; Vista should not need any code change for the new recipient.

**Idempotency / dedup:** each notification row carries an `eventId`. Where multiple recipients are notified for the same event (owner + agent for INSPECTION_REQUESTED; applicant + owner + agent for INSPECTION_CANCELLED), each recipient gets their own row with a distinct `eventId` (the secondary recipients use a deterministic child id derived from the primary). Vista should treat each row independently — never assume eventId is global.

**SSE event:** the existing `notification` SSE event already exists. The kinds above are added to the payload union — no new event channel.

### Vista implementation notes

**Files likely to touch:**

- `components/notifications/NotificationItem.tsx` — branch on `kind` to render the right copy + icon + deep link.
- `components/notifications/NotificationsBell.tsx` — no change needed if it already shows total unread count + opens the inbox.
- `lib/notifications/render.ts` (or wherever the kind→copy mapping lives) — add the three new kinds.

**Copy suggestions per kind:**

| Kind                    | Title                      | Body (uses payload fields)                   | Deep link |
|-------------------------|----------------------------|----------------------------------------------|-----------|
| INSPECTION_REQUESTED    | "New inspection request"   | "A new applicant wants to view your listing." | `/inspections/{id}` |
| INSPECTION_APPROVED     | "Your inspection is confirmed" | "The owner approved your booking — see you on inspection day." | `/inspections/{id}` |
| INSPECTION_DECLINED     | "Inspection declined"      | If `reason` is present: "The owner declined: \"{reason}\"". If null: "The owner declined this inspection." | `/inspections/{id}` |
| INSPECTION_CANCELLED    | "Inspection cancelled"     | "Cancelled by {applicant\|owner\|agent}: \"{reason}\"". | `/inspections/{id}` |

For INSPECTION_CANCELLED, derive "which party cancelled" by comparing `payload.cancelledByUserId` against `payload.applicantId` / `payload.ownerId` / `payload.agentUserId`.

**Real-time UX:**

- SSE push → toast pops with title + 1-line body + click-to-open.
- Bell icon's unread badge bumps.
- If the user is currently on the inspection-detail page that matches the notification's `inspectionRequestId`, refresh the status block silently (no toast needed — they're already looking at it).

### Test plan (Vista side)

- **Gap A — agent fan-out:** as Temi (applicant), book an inspection on a listing where Emeka is the active agent. Open notification bells for both Amaka (owner) and Emeka (agent) — both should see the `INSPECTION_REQUESTED` row.
- **Gap A — no agent:** book on a listing without an active agent. Owner gets the notification; the agent fanout is silently skipped (no errors).
- **Gap A — agent equals owner edge case:** rare but possible — Amaka is both owner and agent on a listing. Only one notification row, not two.
- **Gap B — approve:** as Amaka, approve a PENDING request. Temi's bell pings within ~1s (Kafka relay latency).
- **Gap B — decline with reason:** if a future UI captures the reason, supply one. Temi sees "Owner declined: \"<reason>\"". Otherwise: "Owner declined this inspection."
- **Gap B — decline without reason:** payload `reason: null` → fall back copy.
- **Idempotency:** restart a Kafka consumer mid-delivery (dev environment only) — verify Temi doesn't see two rows for the same approval.
- **SSE reconnect:** kill + restore the SSE connection while a notification fires — verify the next pull via `GET /api/notifications/mine` shows the row even if the SSE push was missed.

### What NOT to do

- Don't show the agent's INSPECTION_REQUESTED notification as a different copy than the owner's — backend ships identical payload; Vista renders identically. Differentiation by recipient (e.g. "you've been asked to facilitate this") is a future enhancement, not part of this push.
- Don't assume `payload.reason` is always present on INSPECTION_DECLINED — the legacy decline path doesn't capture a reason yet.
- Don't render the raw `payload` JSON — branch on `kind` and select the right copy template.

---

## VTASK-006 — Comment threading (parent/child)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 8
**Backend status:** ✅ shipped (V43 migration + service + controller + tests, uncommitted on branch `lukasio`)

### Why this matters

Comments today are flat. Owner can't reply to a specific applicant question; multiple Q&A threads on one listing get tangled. Threading is a core UX expectation for any listing-Q&A surface.

### API contract

**Post a top-level comment or reply** — `POST /api/listings/{listingId}/comments`

Auth: `Authorization: Bearer <jwt>` (any role: OWNER, AGENT, APPLICANT, ADMIN).

Request body:

```json
{
  "body": "Yes, fully redone in 2024.",
  "parentCommentId": 5
}
```

- `body` (string, required) — 1..4000 chars; blank rejected with 400.
- `parentCommentId` (number, optional) — when supplied, this comment is a reply to that parent. Omit (or send `null`) for a top-level comment.

Success — `201 Created`:

```json
{
  "id": 7,
  "listingId": 17,
  "authorUserId": 42,
  "body": "Yes, fully redone in 2024.",
  "parentCommentId": 5,
  "createdAt": "2026-05-09T18:45:00Z"
}
```

Errors (RFC 7807 ProblemDetail):

| Status | `type` suffix | When |
|---|---|---|
| 400 | `validation-failed` | Body blank / too long, or `parentCommentId` references a soft-deleted parent OR a parent on a different listing. |
| 401 | `unauthenticated` | No / invalid JWT. |
| 404 | `not-found` | Listing not found, or `parentCommentId` references a non-existent comment. |

**List comments** — `GET /api/listings/{listingId}/comments?page=0&size=20`

Auth: public, no JWT required. Paginated, oldest-first.

Success — `200 OK`:

```json
{
  "content": [
    {
      "id": 5,
      "listingId": 17,
      "authorUserId": 89,
      "body": "Is the kitchen renovated?",
      "parentCommentId": null,
      "createdAt": "2026-05-09T18:30:00Z"
    },
    {
      "id": 7,
      "listingId": 17,
      "authorUserId": 42,
      "body": "Yes, fully redone in 2024.",
      "parentCommentId": 5,
      "createdAt": "2026-05-09T18:45:00Z"
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 2, "totalPages": 1 }
}
```

Each entry carries `parentCommentId` (null for top-level, set for replies). Soft-deleted rows are excluded server-side — Vista never sees them.

### Vista implementation notes

**Tree assembly (client-side).** The wire shape is intentionally flat. Group entries on `parentCommentId`:

```ts
type CommentNode = CommentResponse & { children: CommentNode[] };

function buildTree(flat: CommentResponse[]): CommentNode[] {
  const byId = new Map<number, CommentNode>(
    flat.map(c => [c.id, { ...c, children: [] }])
  );
  const roots: CommentNode[] = [];
  for (const node of byId.values()) {
    if (node.parentCommentId && byId.has(node.parentCommentId)) {
      byId.get(node.parentCommentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}
```

A reply whose parent isn't in the same page (orphan after paging) falls back to a top-level row — acceptable for v1.

**Rendering**:

- Each comment shows a "Reply" button. Tap → opens a small textarea inline below it.
- Submitting calls `POST /api/listings/{listingId}/comments` with `parentCommentId` set to the parent's id.
- Visual indentation: cap at **3 levels** of nesting. Replies at depth 4+ render under their depth-3 ancestor as flat siblings, with a small "in reply to @AuthorName" label.
- After successful post, refetch the listing's comments OR append the returned row to local state in the right tree position.

**Files likely to touch**:
- `components/listing/CommentsSection.tsx` (or equivalent) — switch from flat list to tree render
- `lib/api/comments.ts` — add optional `parentCommentId` to the `postComment()` helper
- `types/comments.ts` — add `parentCommentId: number | null` to the response type

**Copy suggestions**:
- Reply button: "Reply"
- Submitting state: "Posting reply…"
- Error toast (400 parent invalid / deleted): "That comment was removed — refresh the page."
- Error toast (404 parent): "That comment no longer exists — refresh the page."

### Test plan

- Post a top-level comment → see it as a root row.
- Post a reply → see it indented under the parent.
- Reply to a reply → indented one more level (cap at 3).
- Reply to a comment that was just deleted in another tab → 400 surfaces a clear error toast.
- Refetch / reload preserves the tree shape from the flat list.
- Public (unauthenticated) view shows the threaded tree — no auth needed.

### What NOT to do

- **Don't try to nest on the server.** The contract is flat-with-parent-id by design — paging, sort, and caching all stay simple.
- **Don't enforce depth on the client by blocking the Reply button at level 3** — just collapse the indentation. Conversations need to keep going even visually flat.

---

## VTASK-007 — "Can I review?" pre-check + post-close review CTA

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 9
**Backend status:** ✅ shipped (new endpoint + tests, uncommitted on branch `lukasio`)

### Why this matters

After a deal closes, eligible parties should see a "Review the owner" / "Review the agent" CTA on the listing detail page. Today Vista can't know who's eligible without trying to POST and reading the 403. A pre-check endpoint lets Vista render the CTA conditionally, with no wasted round trip and no jarring error toast.

### API contract

**Pre-check** — `GET /api/listings/{listingId}/reviews/me/eligibility`

Auth: `Authorization: Bearer <jwt>` (any authenticated user). Returns 200 even when neither side is eligible — eligibility is data, not an error.

Response shape:

```ts
type ReviewEligibilityResponse = {
  listingStatus: "DRAFT" | "LIVE" | "CLOSED" | "PAUSED" | "TAKEN_DOWN";
  canReviewOwner: boolean;
  canReviewAgent: boolean;
  ownerUserId: number;
  agentUserId: number | null;   // null when no ACCEPTED agent on this listing
  reasons: {
    owner: string | null;  // null = eligible; otherwise a short human reason
    agent: string | null;
  };
};
```

**Both sides eligible** — closed deal with both counterparties:

```json
{
  "listingStatus": "CLOSED",
  "canReviewOwner": true,
  "canReviewAgent": true,
  "ownerUserId": 50,
  "agentUserId": 77,
  "reasons": { "owner": null, "agent": null }
}
```

**Owner only** — closed deal with no agent involved:

```json
{
  "listingStatus": "CLOSED",
  "canReviewOwner": true,
  "canReviewAgent": false,
  "ownerUserId": 50,
  "agentUserId": null,
  "reasons": {
    "owner": null,
    "agent": "This listing has no assigned agent to review."
  }
}
```

**Neither eligible** — listing not yet CLOSED:

```json
{
  "listingStatus": "LIVE",
  "canReviewOwner": false,
  "canReviewAgent": false,
  "ownerUserId": 50,
  "agentUserId": 77,
  "reasons": {
    "owner": "Reviews open once the listing is CLOSED — current status is LIVE.",
    "agent": "Reviews open once the listing is CLOSED — current status is LIVE."
  }
}
```

Errors:

| Status | `type` suffix | When |
|---|---|---|
| 401 | `unauthenticated` | No / invalid JWT. |
| 404 | `not-found` | Listing not found. |

### Vista implementation notes

- On the listing-detail page, when `status === "CLOSED"`, fire this GET once on mount (cache with `staleTime: 60_000` — eligibility doesn't change while the user looks at the page).
- Render zero, one, or two CTAs based on the booleans:
  - `canReviewOwner && canReviewAgent` → two buttons: "Review the owner" and "Review the agent".
  - `canReviewOwner` only → one button: "Review the owner".
  - `canReviewAgent` only → one button: "Review the agent".
  - neither → render nothing (don't show "you can't review this" — silent absence is the right UX).
- Clicking either button navigates to the review-form modal/page with the appropriate `revieweeUserId` (use `ownerUserId` or `agentUserId` from the eligibility response). The subsequent `POST /api/listings/{id}/reviews` enforces the same rules server-side.
- When status is not CLOSED, **don't fire the request at all** — just hide the CTA section. Saves a backend call per page view.

**Files likely to touch**:
- `lib/api/reviews.ts` — add `getReviewEligibility(listingId)` helper.
- `components/listing/ListingDetailPage.tsx` — wire the conditional CTA block.
- `types/reviews.ts` — add the response type.

**Copy suggestions**:
- Single owner CTA: "Review {ownerDisplayName}"
- Single agent CTA: "Review {agentDisplayName}"
- Both CTAs side-by-side: "Review the owner" / "Review the agent"
- Tooltip on disabled (if you choose to render disabled): the `reasons` string

### Test plan

- Authenticated as a user who won the offer on a CLOSED listing without an agent → owner CTA appears, no agent CTA.
- Authenticated as the same user on a CLOSED listing WITH an ACCEPTED agent → both CTAs appear.
- Authenticated as a user who lost the offer (`hadAcceptedOffer == false`) on a CLOSED listing → no CTAs rendered, no error displayed.
- Authenticated as the owner of the closed listing → no CTAs (`canReviewOwner=false` since self, `canReviewAgent=false` since not an applicant).
- Listing not yet CLOSED → CTAs hidden; eligibility request not fired.
- Unauthenticated → CTA section hidden entirely (don't even attempt the call).
- 404 (listing genuinely missing — race) → fall through to the page's existing not-found handler.

### What NOT to do

- Don't post a review without checking eligibility first — POST will fire 403/404/409 if used cold; conditional CTAs avoid the error path entirely.
- Don't cache eligibility across listings — it's listing-scoped. Use the listing id in the cache key.
- Don't render the `reasons.*` strings as the primary UX. They're a fallback diagnostic for support, not a CTA replacement.

---

## VTASK-008 — Wire up the existing comment-flag endpoint

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 10
**Backend status:** ✅ shipped (endpoint already lived in code; OpenAPI annotations expanded — uncommitted on branch `lukasio`)

### Why this matters

Backend already had comment-flagging (users reporting abusive comments) but Vista doesn't expose it. Trivial frontend wiring; meaningful trust+safety win.

### API contract

**Flag a comment** — `POST /api/listings/{listingId}/comments/{commentId}/flag`

Auth: `Authorization: Bearer <jwt>` (any role: OWNER, AGENT, APPLICANT, ADMIN).

Request body (optional):

```json
{ "reason": "spam — selling unrelated services" }
```

- `reason` (string, optional) — up to 512 chars. Shown to admins in the moderation queue. Empty/whitespace becomes `null`.
- Sending an empty body (`{}` or no body at all) is allowed — the flag is recorded without a reason.

Success — `201 Created`:

```json
{
  "id": 12,
  "listingId": 17,
  "commentId": 5,
  "reporterUserId": 89,
  "reason": "spam — selling unrelated services",
  "status": "OPEN",
  "createdAt": "2026-05-10T10:00:00Z"
}
```

Errors:

| Status | `type` suffix | When |
|---|---|---|
| 400 | `validation-failed` | `reason` exceeds 512 chars. |
| 401 | `unauthenticated` | No / invalid JWT. |
| 404 | `not-found` | Comment not found on this listing (mismatched listing/comment ids, or the comment is hard-deleted). |
| 409 | `conflict` | The caller already has an OPEN flag against this comment — wait for the moderator's decision before re-flagging. |

After an admin transitions the flag to `RESOLVED` or `DISMISSED`, the same reporter CAN flag again (the partial unique index is on status=OPEN only).

### Vista implementation notes

- Each comment row gets a "⋯" menu in the top-right corner. Items:
  - `Flag this comment` — opens a small dialog asking for an optional reason (max 512 chars), with a `Submit flag` button.
  - The menu also already has `Delete` for the comment's author / owner / admin (existing flow).
- Dialog UX:
  - Textarea + `Cancel` / `Submit` buttons.
  - "Reason (optional)" placeholder. Helper text under the field: "We share this with moderators only."
  - Submit → POST. On 201, toast: "Reported. We'll review this comment." Close dialog.
  - On 409, toast: "You've already flagged this comment — we're reviewing it." Disable the flag option for this comment for the rest of the session.
  - On 400 (>512 chars), inline error under the textarea.
- Per-session state: keep a `Set<number>` of comment ids the user has flagged this session so the menu item shows `Already flagged` (disabled). Reset on logout / new session — we don't have a "is this comment flagged by me" endpoint.

**Files likely to touch**:
- `components/listing/CommentRow.tsx` (or similar) — add the ⋯ menu + flag option.
- `components/listing/FlagCommentDialog.tsx` — new modal.
- `lib/api/comments.ts` — add `flagComment(listingId, commentId, reason)`.
- `state/flaggedComments.ts` — session-local set of already-flagged ids.

**Copy suggestions**:
- Menu item: "Flag this comment"
- Dialog title: "Report a comment"
- Dialog placeholder: "What's wrong with this comment? (optional)"
- Submit button: "Submit flag"
- Success toast: "Reported. We'll review this comment."
- Conflict toast: "You've already flagged this comment — we're reviewing it."

### Test plan

- Flag a comment with a reason → 201, toast appears, menu item disables for that comment.
- Flag a comment without a reason (just submit empty) → 201.
- Flag the same comment again in the same session → menu item is already disabled; if you bypass by reloading, second POST returns 409 and shows the conflict toast.
- Flag a comment that belongs to a different listing (manually craft URL with wrong listingId) → 404; toast: "That comment no longer exists."
- Flag while unauthenticated → menu item should not be visible at all; if a stale UI somehow sends the request → 401 redirects to login.

### What NOT to do

- Don't auto-take-down a flagged comment. The flag opens a moderation queue row — only an admin (or the owner/author themselves via the delete endpoint) can soft-delete the comment.
- Don't show the reporter the moderator's decision. There's no API for that today; the reporter just sees the comment gone (if RESOLVED via takedown) or unchanged (if DISMISSED).
- Don't expose `GET /api/admin/comment-flags` to non-admins. Admin-only.

---

## VTASK-009 — Agent review eligibility (post-deal)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 11
**Backend status:** ✅ shipped (`ReviewService.post` extended + eligibility shipped via VTASK-007 — uncommitted on branch `lukasio`)

### Why this matters

Emeka does all the work on Amaka's listing — but today applicants can only review Amaka (the owner). After this fix, applicants on closed deals can also review the agent, giving agents a public rating that helps them get more assignments.

### API contract

**Post a review** — `POST /api/listings/{listingId}/reviews` (existing endpoint, **revieweeUserId now accepts owner OR agent**)

Auth: `Authorization: Bearer <jwt>`.

Request body unchanged:

```json
{
  "revieweeUserId": 77,
  "rating": 5,
  "body": "Agent did all the heavy lifting. Highly recommend."
}
```

Validation rules (server-side):

- Listing must be `CLOSED` (else 409 with `listing.not-closed`).
- Caller must have an `ACCEPTED` offer on this listing (else 403 with `not-a-deal-participant`).
- `revieweeUserId` must be either:
  - The listing's `ownerId`, OR
  - A user with an `ACCEPTED` row in `agent_listings` for this listing.
  - Anything else → 403 with `invalid-reviewee`.
- Self-review rejected (403 with `invalid-reviewee`).
- Duplicate `(listingId, reviewerUserId, revieweeUserId)` → 409 with `duplicate-review`.

Success — `201 Created`:

```json
{
  "id": 14,
  "listingId": 17,
  "reviewerUserId": 89,
  "revieweeUserId": 77,
  "rating": 5,
  "body": "Agent did all the heavy lifting. Highly recommend.",
  "createdAt": "2026-06-15T14:00:00Z"
}
```

**Pre-check before posting** — use the Item 9 eligibility endpoint from VTASK-007:

`GET /api/listings/{listingId}/reviews/me/eligibility`

It tells Vista exactly which reviewee ids are eligible (`ownerUserId`, `agentUserId`) and which CTA(s) to render. See VTASK-007 for the full shape.

Errors on POST:

| Status | `type` suffix | When |
|---|---|---|
| 400 | `validation-failed` | Rating out of [1,5], body blank / > 2000 chars. |
| 401 | `unauthenticated` | No / invalid JWT. |
| 403 | `forbidden` | Self-review, not a deal participant, or revieweeId is neither owner nor accepted agent. |
| 404 | `not-found` | Listing not found. |
| 409 | `conflict` | Listing not CLOSED, or duplicate review. |

### Vista implementation notes

Pairs with VTASK-007. The flow is:

1. On listing-detail page where `status === "CLOSED"`:
   1. Call `GET /api/listings/{id}/reviews/me/eligibility`.
   2. Render zero, one, or two CTAs based on `canReviewOwner` + `canReviewAgent`.
2. User taps "Review the agent" → modal opens with `revieweeUserId = agentUserId` (from the eligibility response) pre-filled and immutable in the form.
3. User picks a rating (1-5 stars) + writes body → `POST /api/listings/{id}/reviews`.
4. On 201, close modal, show toast "Review submitted", refetch the listing-reviews list.
5. On 409 (`duplicate-review`), toast "You've already reviewed this person on this listing." — and ideally pre-empt this by hiding the CTA when the reviews list already contains the caller's row for that reviewee.

The aggregate (`averageRating`, `reviewCount`) on the agent's public profile updates immediately — Vista's agent-detail page picks up the new average on next fetch.

**Files likely to touch**:
- `components/listing/ReviewCTAs.tsx` — render the conditional buttons (see VTASK-007 for the eligibility integration).
- `components/listing/PostReviewModal.tsx` — accept `revieweeUserId` + `revieweeRole` ("owner" | "agent") to render the right copy.
- `lib/api/reviews.ts` — `postReview(listingId, { revieweeUserId, rating, body })`.

**Copy suggestions**:
- Modal title (agent): "Review {agentName}"
- Modal title (owner): "Review {ownerName}"
- Subhead: "Your review is public on their profile and will help future renters / buyers."
- Submit button: "Post review"
- Success toast: "Review submitted."
- Conflict toast: "You've already reviewed this person on this listing."

### Test plan

- Logged in as the winning applicant on a CLOSED listing where Amaka is owner AND Emeka is the ACCEPTED agent → eligibility returns both true → both CTAs render. Submit "Review the agent" with rating 5 → 201; Emeka's profile aggregate updates. Submit "Review the owner" with rating 4 → 201; Amaka's profile aggregate updates.
- Submit a second review on the same agent for the same listing → 409 (`duplicate-review`); appropriate toast.
- Try to review someone who's neither owner nor the listing's accepted agent → 403 (`invalid-reviewee`); shouldn't normally be reachable because the modal is gated on eligibility.
- Logged in as an applicant whose offer was DECLINED → eligibility returns both false; CTAs not rendered.
- Logged in as the agent reviewing themselves → eligibility hides the agent CTA; if bypassed, POST returns 403.

### What NOT to do

- Don't show the agent CTA on listings without an ACCEPTED agent (`agentUserId === null`). The eligibility endpoint already returns `canReviewAgent=false` with an explanation in that case.
- Don't try to render an "average agent rating" on the listing card — agents have their own profile page where the aggregate appears. Listing cards stay clean.
- Don't allow editing or re-submitting a review. Reviews are immutable; if a reviewer wants to retract, they `DELETE /api/reviews/{id}` (their own only).

---

## VTASK-010 — Pre-signed photo upload (browser → R2 direct)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 2
**Backend status:** ✅ shipped (uncommitted on branch `lukasio` — V46 migration, new endpoints, service, presigned-storage abstraction, scheduled cleanup, unit + controller + IT tests)

### Why this matters

Today photo uploads proxy through Haven (`POST /api/listings/{id}/photos` with multipart). Works at our scale but consumes Haven's bandwidth + memory; won't survive 100+ concurrent uploads. The two-step pre-signed flow lets the browser PUT bytes directly to R2 — Haven only mints a URL up front and confirms metadata afterward. Both flows coexist (the legacy multipart endpoint keeps working), so Vista migrates gallery-by-gallery without a flag day.

### API contract

**Both endpoints require `Authorization: Bearer <jwt>` and `Content-Type: application/json`. Caller must be the listing owner OR an active assigned agent (an `agent_listings` row with `status=ACCEPTED`).**

#### 1. Mint pre-signed URL — `POST /api/listings/{listingId}/photos/upload-url`

Request body:

```json
{
  "contentType": "image/jpeg",
  "sizeBytes": 523456,
  "originalFilename": "living-room.jpg"
}
```

| Field              | Type   | Required | Validation                                    |
|--------------------|--------|----------|-----------------------------------------------|
| `contentType`      | string | yes      | Must be `image/jpeg`, `image/png`, or `image/webp`. |
| `sizeBytes`        | int    | yes      | `> 0` and `<= 10485760` (10 MiB).             |
| `originalFilename` | string | no       | ≤ 255 chars; used only to slug the object key. |

Success — `201 Created`:

```json
{
  "uploadUrl": "https://<account-id>.r2.cloudflarestorage.com/listings/17/abc-uuid-living-room.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...&X-Amz-Date=...&X-Amz-Expires=600&X-Amz-Signature=...&X-Amz-SignedHeaders=host%3Bcontent-type%3Bcontent-length",
  "fileKey": "listings/17/abc-uuid-living-room.jpg",
  "expiresAt": "2026-05-24T10:00:00Z",
  "maxSizeBytes": 10485760,
  "allowedContentTypes": ["image/jpeg", "image/png", "image/webp"]
}
```

The browser MUST then `PUT` raw image bytes to `uploadUrl` using the same `contentType` and `sizeBytes` that were sent in the request — the URL is bound to those values and R2 will refuse mismatches.

Errors (all RFC 7807 ProblemDetail):

| Status | `type` suffix          | When                                         | Vista copy suggestion |
|--------|------------------------|----------------------------------------------|-----------------------|
| 400    | `validation-failed`    | `contentType` not in allow-list, OR `sizeBytes` out of bounds (e.g. > 10 MB), OR JSR-303 bean validation fails (e.g. `sizeBytes` ≤ 0). | "Pick a JPEG, PNG, or WebP under 10 MB." |
| 401    | `unauthenticated`      | No / invalid JWT.                            | Redirect to login.   |
| 403    | `forbidden`            | Caller is neither the listing owner nor an active assigned agent. | "Only the listing's owner or assigned agent can add photos." |
| 404    | `not-found`            | Listing doesn't exist.                       | Standard "listing not found" handling. |

#### 2. Confirm upload — `POST /api/listings/{listingId}/photos/confirm`

Request body:

```json
{
  "fileKey": "listings/17/abc-uuid-living-room.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 523456,
  "width": 1920,
  "height": 1280,
  "caption": "Living room facing the lagoon"
}
```

| Field         | Type   | Required | Notes                                                       |
|---------------|--------|----------|-------------------------------------------------------------|
| `fileKey`     | string | yes      | Verbatim from the mint response.                            |
| `contentType` | string | yes      | The MIME type that was uploaded.                            |
| `sizeBytes`   | int    | yes      | Actual bytes PUT. **Must match the size R2 reports** (else 422). |
| `width`       | int    | no       | Image width in px; metadata only — not validated against R2. |
| `height`      | int    | no       | Image height in px; metadata only.                          |
| `caption`     | string | no       | Free-text caption rendered next to the photo (≤ 255 chars). |

Success — `201 Created` (shape matches the existing multipart endpoint's `PhotoResponse`):

```json
{
  "id": 88,
  "listingId": 17,
  "url": "https://media.dreamhomes.com/listings/17/abc-uuid-living-room.jpg",
  "displayOrder": 3,
  "caption": "Living room facing the lagoon",
  "uploadedAt": "2026-05-10T09:00:00Z"
}
```

`displayOrder` is server-assigned (max+1) so concurrent uploads on the same listing don't collide.

Errors:

| Status | `type` suffix          | When                                           | Vista copy suggestion |
|--------|------------------------|------------------------------------------------|-----------------------|
| 400    | `validation-failed`    | Missing fields, `sizeBytes` ≤ 0, etc.          | "Something went wrong with the upload — try again." |
| 401    | `unauthenticated`      | No / invalid JWT.                              | Redirect to login.   |
| 403    | `forbidden`            | Caller is not the listing owner / active agent, OR the intent row belongs to a different caller (someone tried to confirm another user's intent). | "Only the listing's owner or assigned agent can confirm uploads." |
| 404    | `not-found`            | Listing doesn't exist.                         | Standard not-found.  |
| 409    | `conflict`             | `fileKey` was never issued, already confirmed, or expired. | "Upload session expired — start over." (Re-mint a URL.) |
| 422    | `moderation-blocked`   | R2 HEAD reports the object is missing, OR its size doesn't match the claimed `sizeBytes`. | "Upload didn't land — try again." |

> Note: `422` carries the `moderation-blocked` `type` URI under the current generic mapping. The shape is `application/problem+json`; the `detail` field carries a developer-readable reason. Vista should branch on `status` + the new `type` suffix when we mint one specifically for this case (probably `photo-upload-failed-verification` in a future tweak — for now, branch on the 422 status alone is fine).

### Vista implementation notes

**Files likely to touch:**

- `components/listings/PhotoUploader.tsx` (or wherever the multipart upload currently lives) — add a two-step path behind a feature flag / capability check, falling back to the existing multipart endpoint when needed.
- `lib/api/photos.ts` — add `mintUploadUrl(listingId, { contentType, sizeBytes, originalFilename })` and `confirmUpload(listingId, { fileKey, contentType, sizeBytes, width?, height?, caption? })` helpers alongside the existing multipart-proxy helper.
- `lib/upload/r2.ts` (NEW) — a thin wrapper around the browser `fetch` PUT to R2 with progress callback (use `XMLHttpRequest` for upload-progress events; `fetch` doesn't expose them).

**Two-step upload dance (TypeScript pseudocode):**

```ts
async function uploadPhotoDirect(
  listingId: number,
  file: File,
  caption?: string,
  onProgress?: (pct: number) => void
): Promise<PhotoResponse> {
  // 1. Mint pre-signed URL
  const mint = await api.post(`/api/listings/${listingId}/photos/upload-url`, {
    contentType: file.type,
    sizeBytes: file.size,
    originalFilename: file.name,
  });

  // 2. PUT bytes directly to R2
  await putToR2(mint.uploadUrl, file, file.type, onProgress);

  // 3. Confirm — server HEADs R2 + writes listings_photos row
  return await api.post(`/api/listings/${listingId}/photos/confirm`, {
    fileKey: mint.fileKey,
    contentType: file.type,
    sizeBytes: file.size,
    caption,
  });
}

function putToR2(url: string, blob: Blob, contentType: string,
                 onProgress?: (pct: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    xhr.setRequestHeader("Content-Type", contentType);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress((e.loaded / e.total) * 100);
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(new Error(`R2 PUT failed: ${xhr.status} ${xhr.responseText}`));
    };
    xhr.onerror = () => reject(new Error("R2 PUT network error"));
    xhr.send(blob);
  });
}
```

**Progress UI:**

- Show a percentage bar bound to the `onProgress` callback above. R2 PUTs of 10 MB on a 5 Mbps mobile connection take ~16 seconds — visible feedback is mandatory.
- Three phases: "Requesting upload slot…" (mint call, usually < 200 ms), "Uploading X%…" (the PUT, dominant), "Confirming…" (the confirm call, usually < 500 ms).

**Retry logic:**

- Mint call: retry once on network error or 5xx (idempotent — produces a new fresh intent row each call).
- R2 PUT: do NOT retry blindly. If the PUT fails partway, re-mint a fresh URL and start over. The pre-signed URL is single-use; resuming a half-failed PUT against the same URL is racy.
- Confirm call: retry once on network error. If the server returns 409 (`fileKey` already consumed) on a retry, the previous attempt actually succeeded — fetch `GET /api/listings/{id}/photos` to surface the photo and treat as success.

**Fallback to multipart if needed:**

- Detect: if the mint call fails with 500/503, or if `putToR2` repeatedly fails (CORS misconfig on R2, no internet, etc.), fall back to the legacy `POST /api/listings/{id}/photos` multipart endpoint for THIS upload.
- A user-toggleable "Use slower direct upload" / "Use server-proxied upload" preference (in dev tools) is overkill for production; the auto-fallback above is sufficient.

**Edge cases to handle:**

- **URL expiry mid-upload** — the URL is good for 10 minutes. On a 14-second 10 MB PUT this never bites. On a 30-second 50 MB PUT (we cap at 10 MB so this can't happen), the PUT would fail with a R2 403. Vista should display "Upload took too long — try again" and re-mint.
- **File too big** — block client-side BEFORE calling mint: `if (file.size > 10 * 1024 * 1024) { showError("Max file size is 10 MB"); return; }`. The mint call also rejects with 400 if the client missed this check.
- **Wrong content type** — same: block client-side AND let the mint call's 400 be the safety net.
- **Browser closes the tab mid-PUT** — the intent row stays unconfirmed until the hourly cleanup job runs (or it ages out at 24h). No visible side effect; user re-uploads on next session.
- **Caption typo recovery** — there's no PATCH endpoint on `listing_photos` today. Editing a caption means delete + re-upload. Acceptable for v1.
- **Concurrent uploads on the same listing** — fine. Each gets its own intent + UUID-based fileKey. `displayOrder` is server-assigned (max+1) on confirm, so ordering is deterministic even when two uploads finish out-of-order.

### Test plan

**Manual scenarios against `haven.dreamhomes.today`:**

1. **Happy path — owner uploads via direct PUT:** log in as Amaka, navigate to one of her listings → photos section, pick a JPEG. Verify mint returns a URL, the PUT to R2 succeeds (progress bar advances), and the confirm response embeds the new photo. Reload — the photo appears in the gallery at the next display order.
2. **Happy path — active agent uploads on a listing they manage:** log in as Emeka, find a listing where he has an ACCEPTED `agent_listings` row, upload a photo. Confirm 201 + visible in the gallery.
3. **403 — unauthorized agent:** log in as an agent who has NO accepted assignment on the target listing → mint call returns 403. Vista should hide the upload UI entirely for this case (mirror server-side check using `agent-listings/mine`).
4. **400 — wrong content type:** pick a `.pdf` — Vista's client-side guard should block before the mint call. Bypass via DevTools to confirm the server also returns 400.
5. **400 — oversized file:** pick a 12 MB JPEG — same as above (client guard primary; server 400 secondary).
6. **409 — re-confirming the same fileKey:** in DevTools, manually re-fire the confirm POST after the first one succeeded. Verify 409 + Vista shows a soft error (not a hard crash). Alternative: re-mint and the new fileKey works.
7. **422 — missing object in R2:** mint a URL but never PUT (close the browser tab mid-mint), then call confirm with the returned fileKey. Verify 422.
8. **Fallback to multipart:** simulate R2 being down by blocking the R2 domain in DevTools Network tab → the direct PUT fails → Vista's fallback should call the multipart endpoint and succeed silently.

**Visual states:**

- Loading spinner during mint call (< 200 ms — often invisible).
- Indeterminate spinner during the PUT until the first progress event, then percentage bar.
- Spinner during confirm.
- Success — toast + photo appears in gallery.
- Error toasts per status code (copy table above).

### What NOT to do

- **Don't bypass the confirm step.** R2 PUT success alone doesn't register the photo on the listing — the confirm POST writes the `listings_photos` row. Skip confirm and the photo's uploaded to R2 but invisible to the gallery (cleaned up after 24h).
- **Don't reuse a `fileKey` across uploads.** Each mint produces a unique UUID-prefixed key. Trying to confirm with a key that was already consumed returns 409.
- **Don't retry the PUT against the same pre-signed URL.** Re-mint a fresh URL and start over — the old intent row will age out.
- **Don't trust the `expiresAt` to the millisecond.** R2's clock may differ from yours by up to a few seconds; if the PUT errors with 403 close to expiry, treat it as expired and re-mint.
- **Don't render the `uploadUrl` to the user.** It contains the signed credential; logging it (e.g. to Sentry) is a credential leak. The user only sees "Uploading…"; the URL is implementation detail.

---

## VTASK-011 — Automated-verification UI (mocked, swappable providers)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 20
**Backend status:** ✅ shipped (uncommitted on branch `lukasio` — see Haven repo working tree)

### Why this matters

Every verification submission now runs through a first-pass automated check before it lands in Dayo's admin queue. **In v1 the provider is MOCKED** — every check returns PASSED with a confidence score plus plausible extracted fields (NIN, name match, document authenticity). v2 will swap to Smile ID / Dojah / Sourcefin via one env var with no caller-side changes.

The admin queue UI should surface what the provider found so Dayo can sanity-check it against the uploaded document in one glance: "Mock provider says PASSED 0.95 with extracted NIN 12345678901 — does that match the C of O?".

The mocked framing must be explicit. Frontend integrators and judges should never wonder whether the score they're looking at is real.

### API contract

**Endpoints affected (already exist; only the response shape gained one new field):**

- `POST /api/verifications` — submission response now includes `automatedChecks`
- `GET /api/verifications/mine` — same field on every paginated row
- `GET /api/admin/verifications` — admin queue rows include the same array under the admin-view shape
- `POST /api/admin/verifications/{id}/approve` and `.../reject` — return the admin view with `automatedChecks`

**New field on `VerificationResponse` (submitter) and `VerificationAdminView` (admin):**

| Field | Type | Semantics |
|---|---|---|
| `automatedChecks` | `AutomatedCheckResultResponse[]` or `null` | Results of the automated provider checks that ran when the verification was submitted. `null` when no checks ran (legacy rows from before this shipped). In v1 every entry carries `providerName: "MOCK"` and `status: "PASSED"`. |

**`AutomatedCheckResultResponse` shape:**

```ts
type AutomatedCheckResultResponse = {
  checkType: "OWNER_IDENTITY" | "AGENT_CREDENTIALS" | "APPLICANT_IDENTITY" | "PROPERTY_DOCUMENTS";
  providerName: string;        // "MOCK" in v1; "SMILE_ID" / "DOJAH" in v2
  status: "PASSED" | "FAILED" | "NEEDS_HUMAN_REVIEW";
  score: number;               // 0.0 - 1.0
  extractedFields: string;     // JSON object as string — e.g. '{"nin":"12345678901","nameMatch":0.98}'
  providerReference: string;   // provider's correlation id, useful for support
  runAt: string;               // ISO-8601 Instant
};
```

**Concrete examples (v1 MOCK):**

OWNER_IDENTITY submission:
```json
{
  "id": 99, "type": "OWNER_IDENTITY", "status": "PENDING",
  "submitterUserId": 7, "targetUserId": 7, "targetPropertyId": null,
  "documentRefs": "{\"kind\":\"C_OF_O\",\"ref\":\"lagos/lekki/2024/00123\"}",
  "submittedAt": "2026-05-24T08:30:00Z",
  "decidedAt": null, "decisionReason": null,
  "automatedChecks": [
    {
      "checkType": "OWNER_IDENTITY",
      "providerName": "MOCK",
      "status": "PASSED",
      "score": 0.95,
      "extractedFields": "{\"nin\":\"12345678901\",\"nameMatch\":0.98,\"documentAuthenticity\":0.96}",
      "providerReference": "mock-owner-99",
      "runAt": "2026-05-24T08:30:00.123Z"
    }
  ]
}
```

PROPERTY_DOCUMENTS submission:
```json
{
  "id": 100, "type": "PROPERTY_DOCUMENTS", "status": "PENDING",
  "targetPropertyId": 42,
  "automatedChecks": [
    {
      "checkType": "PROPERTY_DOCUMENTS",
      "providerName": "MOCK",
      "status": "PASSED",
      "score": 0.95,
      "extractedFields": "{\"titleType\":\"C_OF_O\",\"registryNumber\":\"LAG/2024/00123\",\"documentAuthenticity\":0.94,\"addressMatch\":0.95}",
      "providerReference": "mock-property-100",
      "runAt": "2026-05-24T08:30:00.456Z"
    }
  ]
}
```

**Legacy row (no automated checks ran):**
```json
{
  "id": 7, "type": "OWNER_IDENTITY", "status": "APPROVED",
  "automatedChecks": null
}
```

**Error responses:** none new. Existing 400 / 401 / 403 / 404 / 409 from the submission endpoint still apply.

**v2 swap-the-provider story (informational, no Vista change needed):** when the deploy sets `HAVEN_VERIFICATION_PROVIDER=smile-id`, every `automatedChecks` entry's `providerName` flips to `SMILE_ID`. Same shape, real provider data. Vista should branch on `providerName === "MOCK"` to show the mocked framing.

### Vista implementation notes

**Files likely to touch:**

- `components/admin/AdminVerificationQueue.tsx` — render the new `automatedChecks` block on each queue row, prominently before the manual approve/reject buttons so Dayo sees the automated result first.
- `components/admin/AutomatedCheckBadge.tsx` — NEW. Renders one check result as a status pill (`PASSED` / `FAILED` / `NEEDS_HUMAN_REVIEW`) + the provider name + a small "MOCKED" chip when `providerName === "MOCK"`.
- `components/verifications/MyVerificationStatusCard.tsx` — optional: surface the same automated-check pill on the user's own dashboard so they see "Your submission auto-checked: PASSED — admin review still required".
- `lib/api/verifications.ts` — extend the existing `VerificationResponse` / `VerificationAdminView` TypeScript types with the new `automatedChecks: AutomatedCheckResultResponse[] | null` field; add the `AutomatedCheckResultResponse` type.

**Admin queue layout (recommended):**

```
┌──────────────────────────────────────────────────────────────┐
│ #99  OWNER_IDENTITY  submitted 2 hours ago                   │
│ Submitter: Amaka Okafor (user 7)                             │
│                                                              │
│ Automated check (MOCKED) ─────────────────────────────       │
│ ✅ PASSED  ·  Provider: MOCK  ·  Confidence: 95%             │
│ Extracted: NIN 12345678901, name match 98%                   │
│                                                              │
│ Documents:                                                   │
│ • C of O — lagos/lekki/2024/00123 [view]                     │
│                                                              │
│ [Approve]  [Reject with reason]                              │
└──────────────────────────────────────────────────────────────┘
```

**Copy suggestions:**

- Provider-pill copy when `providerName === "MOCK"`: `MOCKED v1 — admins remain the source of truth`
- Section heading: `Automated pre-check` (followed by the MOCKED chip)
- Status pill copy: `PASSED` (green), `FAILED` (red), `NEEDS_HUMAN_REVIEW` (yellow)
- Extracted fields: render as `key: value` rows, formatted (snake_case → Title Case for keys)

**Behavior:**

- Parse `extractedFields` from the wire string into a JS object: `JSON.parse(row.extractedFields)`. Wrap in try/catch — fall back to rendering the raw string if parsing fails.
- Show the badge inline with the verification card even when `automatedChecks` is empty (`null`): render a small grey "No automated check ran" note for legacy rows so admins know it's not missing UI.
- When `automatedChecks.length > 1` (future-proof — currently always 1), render each as its own pill stacked vertically.

### Test plan (Vista side)

**Manual scenarios against `haven.dreamhomes.today`:**

1. Log in as Amaka (owner), submit a fresh OWNER_IDENTITY verification → confirm the `POST /api/verifications` response includes a populated `automatedChecks` array with `providerName: "MOCK"`, `status: "PASSED"`, `score: 0.95`, non-empty `extractedFields`.
2. Log in as Dayo (admin), navigate to the verification queue → confirm the new row from step 1 shows the automated check pill prominently.
3. Verify the MOCKED chip appears on every check (we're always v1 in production for the demo window).
4. Approve the row — confirm the response also returns `automatedChecks` populated; downstream listings should still get the badge stamp.
5. Submit a PROPERTY_DOCUMENTS verification on a real property — confirm `extractedFields` includes `titleType` and `registryNumber`.
6. Submit AGENT_CREDENTIALS as Emeka — confirm `extractedFields` includes `licenseNumber` and `licenseStatus: "ACTIVE"`.
7. Open an older verification (created before this shipped) — confirm `automatedChecks` is `null` and Vista renders the "no automated check ran" grey note.

**Edge cases:**

- `extractedFields` containing very long strings — confirm the UI wraps gracefully and doesn't overflow the card.
- `score === 1.0` and `score === 0.0` edges — confirm formatting renders as `100%` and `0%` respectively (not `100.000%`).
- An admin views a verification that's already APPROVED — confirm the automated check pill stays visible as audit context (not just on PENDING rows).

**Visual states:**

- Loading skeleton for the queue card while the GET resolves.
- "PASSED MOCKED" pill in green with the dotted-grey MOCKED chip beside it.
- "No automated check ran" grey note for legacy rows.
- Approve / reject buttons stay visible regardless of automated status (we don't auto-approve in v1).

### What NOT to do

- **Don't hide the "MOCKED" framing.** The whole point of this surface is that the integration boundary is real but the data isn't yet. Removing the chip would mislead judges and frontend integrators.
- **Don't auto-approve in the UI based on a PASSED automated check.** v1 routes every submission through admin review regardless of score — the manual approve/reject buttons stay visible. (The auto-approve threshold property exists in config but is intentionally unused in v1; it's a v2 placeholder.)
- **Don't render `extractedFields` raw as a JSON blob to admins** — parse it and render as a key/value list. Falling back to the raw string is only acceptable if `JSON.parse` fails.
- **Don't fetch the automated checks from a separate endpoint** — they're embedded in the existing verification response by design. A separate fetch would defeat the point of the embed.

---

## VTASK-012 — Liveness check UI (mocked, with v2 framing)

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 19
**Backend status:** ✅ shipped (uncommitted on branch `lukasio` — see Haven repo working tree)

### Why this matters

A liveness check before document submission is the canonical anti-fraud step in any real KYC flow ("blink, turn your head, smile on cue — prove you're not a deepfake"). v1 mocks the whole thing — the endpoint always returns PASSED with score 0.97, and the response is tagged `_mocked: true` so nobody is confused. v2 plugs in a real biometric provider behind the same endpoint without changing the caller contract.

For the demo, Vista shows a placeholder camera UI with a "Run mocked check" CTA. The framing is honest: "we built the integration point; the real biometric SDK plugs in here in phase 2".

### API contract

**1. Run a mocked liveness check** — `POST /api/verifications/liveness-check`

Auth: `Authorization: Bearer <jwt>` (any authenticated user).

No request body required (an empty body is fine; future v2 may accept a session-challenge payload).

Success — `201 Created`:

```json
{
  "id": 42,
  "status": "PASSED",
  "score": 0.97,
  "provider": "MOCK",
  "checkedAt": "2026-05-24T08:30:00Z",
  "_mocked": true
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | number | Use as `livenessCheckId` on the verification submit body. |
| `status` | `"PASSED"` | v1 always returns PASSED. |
| `score` | number | v1 always 0.97. |
| `provider` | `"MOCK"` | v1 always MOCK. v2: `"SMILE_ID"` / `"DOJAH"` / `"SOURCEFIN"`. |
| `checkedAt` | string (ISO-8601 Instant) | When the check ran (server time). |
| `_mocked` | boolean | v1 always `true`. Frontend uses this to render the "mocked" framing. |

The `_mocked` field name uses a leading underscore deliberately — flags it as a developer-mode hint rather than a domain attribute. Vista should branch on this field for the "MOCKED v1" UI copy, not on `provider === "MOCK"` (the latter would tightly couple Vista to provider names).

Errors:

| Status | `type` suffix | When |
|---|---|---|
| 401 | `unauthenticated` | No / invalid JWT |

**2. Pass the liveness id into the verification submit** — `POST /api/verifications`

New optional field on the existing request body:

```json
{
  "type": "OWNER_IDENTITY",
  "documentRefs": { "kind": "NIN", "ref": "AB1234567" },
  "livenessCheckId": 42
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `livenessCheckId` | number | optional | Reference to a passed liveness row from step 1. Must belong to the caller AND be unconsumed. Once submitted, the same id cannot be reused. |

Submit endpoint behavior with `livenessCheckId`:

- Validates the liveness row belongs to the caller — if it doesn't exist or belongs to someone else → `403 Forbidden` with `type: forbidden`, detail `"Liveness check {id} was not found for this user"`.
- Validates the liveness row is unconsumed — if a previous submit already used it → `409 Conflict` with detail `"Liveness check {id} has already been consumed"`.
- On success, stamps `consumed_at` so the same id can't be replayed.

Submit endpoint behavior WITHOUT `livenessCheckId`: unchanged. Backwards compatible — existing flows that don't run a liveness check keep working.

Error responses on the submit endpoint with a bad `livenessCheckId`:

| Status | `type` suffix | When | Vista copy suggestion |
|---|---|---|---|
| 403 | `forbidden` | Liveness id doesn't exist or belongs to someone else | "We couldn't verify your liveness check. Please run it again." |
| 409 | `conflict` | Liveness id already consumed by a previous submission | "This liveness check was already used. Please run a fresh one." |

### Vista implementation notes

**The two-step flow:**

1. User reaches the "Submit identity verification" wizard.
2. **Step 1 (NEW):** show the mocked-liveness placeholder UI; user taps "Run mocked check" → POST `/api/verifications/liveness-check` → store the returned `id` in local state.
3. **Step 2 (existing):** user uploads documents → POST `/api/verifications` with `livenessCheckId: <id from step 1>`.
4. On success, show the "Submitted, awaiting admin review" confirmation.

If the user tries to skip step 1 and go straight to step 2, the submit still succeeds (the field is optional). Vista decides whether to enforce the liveness step UX-side; the backend doesn't.

**Files likely to touch:**

- `components/verifications/LivenessCheckStep.tsx` — NEW. Placeholder camera UI + the "Run mocked check" CTA.
- `components/verifications/VerificationSubmissionWizard.tsx` — wire the two-step flow with the liveness id threaded through.
- `lib/api/verifications.ts` — add `runLivenessCheck()` wrapper that POSTs to `/api/verifications/liveness-check`; extend the existing `submitVerification` wrapper to accept an optional `livenessCheckId`.
- TypeScript types: `LivenessCheckResponse` + `livenessCheckId?: number` on the existing `SubmitVerificationRequest` shape.

**Placeholder UI for step 1:**

```
┌──────────────────────────────────────────────┐
│ 📷  (camera placeholder box, dashed border)  │
│                                              │
│ We'd ask you to blink, turn your head, and   │
│ smile here. This is MOCKED for v1 — we'd    │
│ swap in Smile ID's SDK for production.      │
│                                              │
│ [ Run mocked check ]                         │
└──────────────────────────────────────────────┘
```

After tap → call the endpoint → show a small green "✅ Liveness check passed (mocked)" confirmation + "Continue to documents" button.

**Copy suggestions:**

- Step heading: `Step 1 of 2 — Liveness check`
- Body explainer: `Real-world identity verification would ask you to blink and turn your head on cue. For v1 we mocked this step — tap below to continue. The integration point is real; the biometric provider plugs in here in phase 2.`
- Primary CTA: `Run mocked check`
- After success: `✅ Liveness check passed (mocked) — provider: MOCK, score: 0.97`
- Subtle "info" chip: `MOCKED v1`

**State management:**

- Store the `livenessCheckId` in the wizard's local state (React state / form library / equivalent).
- DON'T persist it across page reloads — the server stamps `consumed_at` on the first submission attempt, so a stale id can't be reused.

**Error handling on submit:**

- On 403 with `detail` mentioning "Liveness check" → toast "Your liveness check expired or wasn't found. Run a fresh one." + reset to step 1.
- On 409 → same UX: toast + reset to step 1.

### Test plan (Vista side)

**Manual scenarios against `haven.dreamhomes.today`:**

1. Log in as Amaka, navigate to the identity verification wizard → confirm Step 1 shows the placeholder camera UI with the MOCKED framing.
2. Tap "Run mocked check" → confirm POST `/api/verifications/liveness-check` returns a 201 with `_mocked: true`, `status: "PASSED"`, `score: 0.97`. Confirm Vista stores the `id` and shows the green confirmation.
3. Continue to Step 2, upload a doc, submit → confirm the POST `/api/verifications` body includes `livenessCheckId: <id>` and the response is 201.
4. Submit again with the same `livenessCheckId` (e.g. tap back, hit submit twice) → confirm Vista handles the 409 gracefully (toast + reset to step 1).
5. Manually craft a request with a `livenessCheckId` belonging to a different user → confirm 403 → toast + reset.
6. Submit WITHOUT running step 1 (the field is optional) → confirm the submission still succeeds (backwards compat).
7. After successful submit, refresh the page and confirm the user dashboard shows the new PENDING verification.

**Edge cases:**

- Network failure on step 1 → spinner stays, show a generic retry.
- Browser back button after step 1 — the stored `livenessCheckId` should remain valid until consumed.
- User cancels mid-wizard then returns much later — the liveness id is still unconsumed (no TTL in v1), so it should still work. (v2 may add a TTL.)

**Visual states:**

- Step 1 placeholder camera with dashed border + MOCKED chip clearly visible.
- Loading spinner on "Run mocked check".
- Success confirmation: green checkmark + score readout + "Continue" button.
- Error toast on submit when `livenessCheckId` was foreign or already consumed.

### What NOT to do

- **Don't hide the `_mocked: true` framing.** The whole point of this endpoint is that the integration boundary is honest about being mocked in v1. The placeholder camera box + the MOCKED chip + the "we'd swap in real biometrics for production" copy all matter for the demo and for honesty to integrators.
- **Don't poll the endpoint multiple times to "feel realistic".** A single POST returns the result immediately. The mocked nature should be obvious from the UI, not hidden behind a fake spinner.
- **Don't enforce step 1 on the backend — that's a Vista UX concern.** The backend's `livenessCheckId` field is optional. If Vista wants to gate the wizard, do it client-side.
- **Don't pre-fill or cache the liveness id beyond the current wizard session.** It's consumed on first use; a stale id will 409.
- **Don't auto-skip step 1 if `process.env.SKIP_LIVENESS=true` or similar.** v1 is mocked but the flow should still be visible — judges and integrators learn from seeing the step.

---

## VTASK-013 — Dream AI compare via UI checkbox selection

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 26 sub-task B
**Backend status:** ✅ shipped on branch `lukasio` (uncommitted)

### Why this matters

Today compare requires the user to paste `/listings/17` URLs into chat. Nobody does this. Add a checkbox to each listing card → "Compare selected (N)" button → POSTs the listing ids on a new `compareListingIds` field. Skips the URL-extraction heuristic entirely and routes straight to the AI-backed compare path.

### API contract

**Endpoint:** `POST /api/dream-ai/suggestions` (existing). Same auth rules as today (works anonymous, persists chat when authenticated).

**New optional field on `DreamAiRunTurnRequest`:**

| Field | Type | Required | Semantics |
|---|---|---|---|
| `compareListingIds` | `number[] \| null` | no | When the array has **2–5** entries the orchestrator routes directly to the compare path with those ids, skipping URL extraction. Arrays of size <2 are ignored (need 2+ to compare); arrays longer than 5 are silently capped at 5. URL-paste compare still works for backwards compat. |

**Request example:**

```json
{
  "prompt": "compare these for me",
  "compareListingIds": [17, 42, 89]
}
```

(The `prompt` field can be any short framing — "which is best?" / "compare for a young couple" — and is used as the user-intent argument to the compare LLM call, so the model can weight whatever the user typed.)

**Response shape:** unchanged. `turn.kind` will be `"compare"`; `turn.blocks` carries a `compare` block with `compareListingIds` and (when Anthropic is configured) a `compareReasoning` payload (`recommendedListingId`, `summary`, `perListing[]` with `pros`, `cons`, `bestFor`). Existing rendering for the URL-paste compare path applies unchanged.

**Error responses:** none new. Existing 400 (validation), 401, 422 (moderation), 429 (rate limit), 502 (upstream) apply.

**Edge cases:**

- `compareListingIds: [17]` (size 1) → request falls through to the rank path; the single id is ignored.
- `compareListingIds: [17, 42, 89, 100, 105, 200, 300]` (size 7) → the orchestrator caps at 5.
- One or more ids reference a non-LIVE listing → compare path filters them out. If fewer than 2 remain LIVE, the response is `kind=error` with markdown `"One or more of those listings is no longer LIVE — open each listing to confirm availability."`
- `compareListingIds` + URL pastes both present → `compareListingIds` wins.

### Vista implementation notes

**Files likely to touch:**

- `components/listings/ListingCard.tsx` — add an optional `compareMode` boolean prop. When true, render a checkbox in the corner; emit selection changes to a shared `useCompareSelection()` hook.
- `components/listings/CompareBar.tsx` — NEW. Floating bar at the bottom of the page. Shows "Compare selected (N)" when N >= 2 selected. Disabled at N=1, hidden at N=0, capped at N=5 (extra clicks no-op).
- `state/compareSelection.ts` — NEW. Zustand / context store of `Set<number>` of selected listing ids. Cleared on navigation away from browse / on successful compare submit.
- `lib/api/dreamAi.ts` — extend the existing suggestions POST to accept `compareListingIds: number[] | null`.

**User flow:**

1. User toggles a "Compare" mode on the browse page (or compare-mode is auto-on for the Dream AI surface).
2. Tap checkbox on each listing card to select. Hard cap at 5 — disable further checkboxes once 5 are picked, with a tooltip "Up to 5 listings at a time."
3. Tap "Compare selected (3)" in the floating bar → POST `/api/dream-ai/suggestions` with `{prompt: "Compare these for me", compareListingIds: [17, 42, 89]}`.
4. Render the response in the existing compare UI (carries `compareReasoning` if Anthropic is wired).
5. Clear selection on success; offer a "Back to selection" link on the compare result.

**Copy suggestions:**
- Checkbox label (a11y): "Add this listing to compare"
- Floating bar (1 selected): "Pick one more to compare" (button disabled)
- Floating bar (2-5 selected): "Compare selected ({N})"
- Cap reached toast: "Up to 5 listings can be compared at once — uncheck one to swap."
- Error toast (some listings no longer LIVE): "One or more of your selections is no longer available — refresh and try again."

### Test plan

- Select 2 listings, tap Compare → request body matches `{compareListingIds: [a, b]}` shape; response renders compare cards with reasoning.
- Select 5, attempt to select a 6th → click is no-op; cap toast shows.
- Select 1, attempt to submit → button disabled.
- Select a listing then close-then-re-open the browse page → selection cleared.
- Mix `compareListingIds` with a URL-pasted compare prompt → backend honours `compareListingIds` (verify via the returned listing ids).
- Select 2 listings; manually mark one as TAKEN_DOWN in DevTools / via admin; submit → backend returns `kind=error`; Vista shows the "no longer LIVE" toast.

### What NOT to do

- Don't keep the selection across browser sessions (no localStorage persistence) — it's a per-task choice, not a saved preference.
- Don't allow more than 5 selections; the backend silently caps so a UI that lets the user select 7 then shows only 5 results is confusing.
- Don't bypass the "compare" prompt field — even when sending `compareListingIds`, set the prompt to a short framing (or the user's typed prompt) so the compare LLM has context about what to weight.

---

## VTASK-014 — Dream AI soft fallback on no_results

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 26 sub-task C
**Backend status:** ✅ shipped on branch `lukasio` (uncommitted)

### Why this matters

When Dream AI's strict search returns nothing, today's response is `kind=no_results` with "relax budget, area, or filters" — the user has to guess what to relax. The new soft fallback runs a relaxed embedding search (threshold × 1.5) and surfaces up to 3 close-but-not-perfect matches with a softer copy: "No exact matches; here are 3 close options — want to see them?" Massive UX win for an otherwise dead-end state.

### API contract

**Endpoint:** `POST /api/dream-ai/suggestions` (existing). Response shape only.

**New turn shape when the soft fallback fires:**

```json
{
  "chatId": 100,
  "traceId": "abc-123",
  "turn": {
    "kind": "reply",
    "markdown": "No exact matches; here are 3 close options — want to see them?",
    "blocks": [
      { "type": "listings", "listingIds": [4, 5, 6] }
    ],
    "meta": {
      "inventoryEmpty": false,
      "queryTooStrict": true,
      "degraded": false,
      "provider": "anthropic",
      "traceId": "abc-123"
    }
  },
  "listingIds": [4, 5, 6]
}
```

**How to tell it apart from a normal `kind=reply`:**

- `kind: "reply"` (not `no_results` — these ARE listings the user might want)
- `meta.queryTooStrict: true` AND `markdown` starts with `"No exact matches"`

When BOTH the strict pass AND the broader pass come up empty, the genuine `kind=no_results` is returned with `meta.queryTooStrict: true` and the old fallback markdown (`"Some listings were considered but none ranked high enough — relax budget, area, or filters."`).

**Error responses:** none new.

### Vista implementation notes

**Files likely to touch:**

- `components/dreamAi/AssistantTurn.tsx` (or equivalent) — branch on `meta.queryTooStrict + kind === "reply"` to render the soft-fallback header. Render the listings rail below as usual.
- (Optional) `components/dreamAi/SoftFallbackHeader.tsx` — NEW. A muted callout above the listings rail with the backend-supplied markdown and an explicit "Show these" / "Refine search" pair.

**Rendering:**

- Render the backend-supplied `markdown` as a small heading above the listings carousel.
- Listings rail uses the existing listing-card component (same as for a normal `kind=reply` rail).
- Optionally, render an inline secondary CTA: "Refine search" → focuses the prompt input and clears it. Helps users who want to try a different prompt rather than accept the broader matches.

**Copy suggestions:**

- Header (backend ships): "No exact matches; here are N close options — want to see them?"
- Inline action (Vista adds): `[Refine search]` link / button to the right of the header.

### Test plan

- Submit a deliberately-strict prompt like "5-bedroom mansion under ₦100k in Antarctica" → response carries `kind=reply` + `meta.queryTooStrict=true` + markdown starting with "No exact matches".
- Confirm the listings rail renders the broader-match ids with the standard card component.
- Submit something with truly zero matches (admin-only: temporarily set every listing to PAUSED) → response carries `kind=no_results`; soft fallback NOT triggered.
- Submit a normal prompt → response carries `kind=reply` + `meta.queryTooStrict=false`; standard reply rail.
- Submit "purple elephant tap dance" → broader-match fallback fires; verify 1-3 ids surface.

### What NOT to do

- Don't render the broader matches as if they were exact matches — the header text MUST be visible so the user knows these are "close options", not "matches".
- Don't fire a follow-up backend request for the broader matches; they're already in the same response payload.
- Don't suppress the listings block if `meta.queryTooStrict` is true and `kind === "reply"` — the whole point is to surface them.

---

## VTASK-015 — Adaptive Dream AI clarify chips

**Status:** ✅ READY FOR VISTA
**Backend item:** post-session-tasks.md Item 26 sub-task A
**Backend status:** ✅ shipped on branch `lukasio` (uncommitted)

### Why this matters

Today's clarify chips are 3 hardcoded options regardless of what the user typed. Now the chips returned by the backend are context-aware — when the user types "lekki", the response drops the "Preferred area" chip and only asks about the remaining slots (Budget, Bedrooms, Rent/Buy). When all four slots are detected from the prompt the orchestrator skips the clarify path entirely and proceeds straight to rank.

### API contract

**Endpoint:** `POST /api/dream-ai/suggestions` (existing). Response shape unchanged — chips block already carries an `options` array of `{id, label, sendText}` objects. The change is which chips appear:

| Prompt | Returned chip ids |
|---|---|
| `"lekki"` | `budget`, `bedrooms`, `term` (area dropped — detected) |
| `"3 bedroom"` | `budget`, `area`, `term` (bedrooms dropped) |
| `"under ₦5m for rent in Yaba"` | (clarify skipped entirely — `kind` is `reply` or `no_results`, not `clarify`) |
| `"??"` | `budget`, `area`, `bedrooms`, `term` (all four — none detected) |

The chip ids are stable: `budget`, `area`, `bedrooms`, `term` (rent-or-buy). New `bedrooms` chip is added (previously absent).

### Vista implementation notes

**Vista doesn't need to change much** — it already renders whatever chips the response carries (`turn.blocks` where `type === "chips"`, iterate `options`). The new `bedrooms` chip will render alongside the existing three when present; the existing chips disappear from the response when the user already supplied that slot.

**One small thing to check:** if Vista has any hardcoded "always 3 chips" layout assumption (e.g. CSS grid with 3 columns), relax it so 1-4 chips render gracefully.

**Copy suggestions** — already shipped on the backend chip labels:

- `budget` → "Budget band"
- `area` → "Preferred area"
- `bedrooms` → "Bedrooms"
- `term` → "Rent or buy"

### Test plan

- Type "lekki" → 3 chips rendered, no "Preferred area" chip.
- Type "3 bedroom" → 3 chips rendered, no "Bedrooms" chip.
- Type "under ₦5m for rent in Yaba 3 bedroom" → response is `kind` `reply` or `no_results` (no clarify chips at all).
- Type "??" → 4 chips rendered.
- Tap any chip → `userChoice` follow-up posts the chip's `sendText` as the next prompt; existing behaviour unchanged.

### What NOT to do

- Don't filter the chips on the client to second-guess the backend — the backend already drops the ones the user implied.
- Don't crash if a future backend version adds new chip ids — render any unknown chip as-is using its `label` and `sendText`.

---

## VTASK-016 — Dream AI mode-honesty indicator

**Status:** ⏳ BACKEND IN PROGRESS
**Backend item:** post-session-tasks.md Item 26 sub-task E
**Backend status:** ✅ already exposes `meta.provider` (no backend change needed)

### Why this matters

When `meta.provider` indicates a degraded ranking path, Vista should subtly indicate "Quick search" or similar so users know they're not in full smart-search mode. Truth in advertising. **As of Item 23 (rankMode default behaviour) this now also covers the FAST embeddings-only path, which fires automatically for anonymous traffic — most public visitors will see this indicator on every search.**

### API contract

✅ The `meta.provider` field already exists on Dream AI turn responses. Values:

| `meta.provider` | What it means | When Vista should render the indicator |
|---|---|---|
| `"anthropic"` | Smart pipeline ran — pgvector candidates + Claude rank | No indicator (smart mode is the baseline) |
| `"embeddings-only"` | **NEW (Item 23)** — FAST mode skipped Claude; pgvector NN order returned directly | Render "Quick search" indicator (anonymous users hit this by default) |
| `"stub"` | Anthropic key unavailable — location-substring browse fallback | Render "Quick search" indicator |
| `"compare"` | Compare turn was rendered (URL-paste, conversation-aware, or `compareListingIds`) | No indicator (compare flows are intentional) |
| `"orchestrator"` | Clarify turn | No indicator |
| `"none"` | Error turn | No indicator |

### Vista implementation notes

- Read `response.turn.meta.provider` on every Dream AI response.
- Render a subtle chip when `provider === "stub"` OR `provider === "embeddings-only"`:
  - Label: `Quick search`
  - Tooltip: `"This response used fast keyword + vector matching. Sign in to enable smart ranking."` (for anonymous embeddings-only) OR `"Smart ranking is temporarily unavailable."` (for stub).
- Distinguish the two if useful: `embeddings-only` is intentional cost defence (anonymous users get this on every search); `stub` is degraded operations.

### Test plan

- Anonymous user submits a normal prompt → `provider === "embeddings-only"`; "Quick search" chip appears.
- Authenticated user submits a normal prompt → `provider === "anthropic"`; no chip.
- Authenticated user explicitly POSTs `rankMode: "FAST"` → `provider === "embeddings-only"`; chip appears.
- Anonymous user explicitly POSTs `rankMode: "SMART"` → `provider === "anthropic"` (if key is set); no chip.
- Test against current production — likely returns `"anthropic"` for authenticated calls.

---

## How items get marked READY FOR VISTA

When a backend item from `post-session-tasks.md` ships:

1. The shipping agent or developer updates the corresponding `VTASK-NNN` entry above:
   - Set status to `✅ READY FOR VISTA`
   - Set `Backend status` to `✅ shipped at commit <sha>`
   - Fill in the `API contract` section with exact request/response shapes, examples, error codes
   - Fill in `Vista implementation notes` and `Test plan`
2. Notify Silas / Vista team that VTASK-NNN is ready
3. Cursor / dev picks it up following the rules in `cursor-handoff-prompt.md`

The structure means there's never a "what should the frontend do next" question — the queue is always the source of truth, and items move through the same lifecycle.
