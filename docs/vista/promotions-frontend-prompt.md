# Vista sync — Promotions feature

The Haven backend now has a full **Promotion** module (merged from `origin/main`). This doc tells Vista exactly what's available, the lifecycle, the placements, and the UX work to do. Pair with `cursor-handoff-prompt.md` for the broader Vista task-queue protocol.

> The backend speaks **"promotion"** (not "ad campaign" — that's a different older module). Vista should adopt the same word everywhere user-facing.

---

## Mental model

A **Promotion** is a paid surfacing of a listing (or an agent) on one of three placements. Owner / agent submits → admin approves → it goes ACTIVE on the chosen placement → impressions + clicks tracked → naturally EXPIRES at the end-date OR admin REJECTS / PAUSES / REVOKES.

---

## Endpoints

### Public + sponsor-side (`/api/promotions`)

| Method | Path | Purpose | Auth |
|---|---|---|---|
| `POST /api/promotions` | Create a new promotion (status starts PENDING) | OWNER or AGENT |
| `GET /api/promotions/mine` | List your own promotions (paginated) | Authenticated |
| `GET /api/promotions/{id}` | Read one of your promotions | Sponsor only |
| `GET /api/promotions/{id}/metrics` | Per-promotion metrics (impressions, clicks, CTR) | Sponsor only |
| `GET /api/promotions/homepage-featured` | Currently-ACTIVE promotions for the homepage placement | Public |
| `GET /api/promotions/listing-search-top` | Currently-ACTIVE promotions for the search-results top slot | Public |
| `GET /api/promotions/agent-directory-top` | Currently-ACTIVE promotions for the agent-directory top slot | Public |
| `POST /api/promotions/{id}/impression` | Record an impression (browser fires this on view) | Public (anonymous OK) |
| `POST /api/promotions/{id}/click` | Record a click (browser fires this on tap) | Public (anonymous OK) |

### Admin moderation (`/api/admin/promotions`)

| Method | Path | Purpose |
|---|---|---|
| `GET /api/admin/promotions` | List all promotions (filterable by status) |
| `GET /api/admin/promotions/{id}` | Read any promotion |
| `POST /api/admin/promotions/{id}/approve` | PENDING → ACTIVE |
| `POST /api/admin/promotions/{id}/reject` | PENDING → REJECTED (with reason) |
| `POST /api/admin/promotions/{id}/pause` | ACTIVE → PAUSED |
| `POST /api/admin/promotions/{id}/resume` | PAUSED → ACTIVE |
| `POST /api/admin/promotions/{id}/revoke` | ACTIVE/PAUSED → REVOKED (terminal, with reason) |
| `GET /api/admin/promotions/{id}/metrics` | Same per-promotion metrics endpoint, admin scope |
| `GET /api/admin/promotions/metrics/summary` | Cross-promotion summary (total impressions, CTR by placement, etc.) |

---

## Status lifecycle

```
              ┌──> APPROVED ──> ACTIVE ──> EXPIRED
              │                   │ ▲
   PENDING ───┤                   │ │
              │                   ▼ │
              └──> REJECTED       PAUSED
                                    │
                                    └──> REVOKED
                       (also: ACTIVE ──> REVOKED)
```

Statuses (from `PromotionStatus` enum):

- **PENDING** — submitted, awaiting admin decision
- **ACTIVE** — approved, currently surfacing on the placement
- **PAUSED** — temporarily off (admin-initiated, reversible via resume)
- **REJECTED** — terminal; admin denied at PENDING. Sponsor can submit a new one.
- **REVOKED** — terminal; admin pulled an active one (with reason)
- **EXPIRED** — terminal; reached the promotion's end-date naturally

---

## Placements (`PromotionPlacement` enum)

| Enum value | UI label (use this exactly) | Where Vista renders it |
|---|---|---|
| `HOMEPAGE_FEATURED` | "Featured" | Homepage hero / featured grid |
| `LISTING_SEARCH_TOP` | "Sponsored" | Top of search results (separated visually from organic) |
| `AGENT_DIRECTORY_TOP` | "Featured" | Top of agent directory page |

Note: HOMEPAGE_FEATURED and AGENT_DIRECTORY_TOP both use "Featured" as the user-facing word; LISTING_SEARCH_TOP uses "Sponsored" because search results have a stronger ad-disclosure expectation.

---

## Vista work — what to build

### 1. Sponsor flow (`OWNER` + `AGENT` users)

**Pages:**
- `/promotions/new` — create form
  - Pick a placement (3 options)
  - Pick a target — listing-id for HOMEPAGE_FEATURED + LISTING_SEARCH_TOP, agent profile for AGENT_DIRECTORY_TOP
  - Set start + end date
  - Optional: copy / image override (check the DTO; if not present, the backend uses the listing's existing photo/title)
  - Submit → POST `/api/promotions` → redirect to `/promotions/mine`
- `/promotions/mine` — paginated list of the sponsor's own promotions with status badges
- `/promotions/{id}` — detail page with metrics block

**Status badge component** — render each `PromotionStatus` with a clear colour:
- PENDING → yellow / amber ("Waiting for admin")
- ACTIVE → green ("Live: showing on Homepage" etc.)
- PAUSED → orange ("Temporarily paused by admin")
- REJECTED → red ("Rejected: <reason>")
- REVOKED → red ("Pulled by admin: <reason>")
- EXPIRED → grey ("Run ended")

### 2. Admin moderation (`ADMIN` users)

**Page:** `/admin/promotions`
- Filterable list (default filter: PENDING — that's the action queue)
- Per-row actions matching the lifecycle:
  - PENDING → "Approve" + "Reject" (with reason modal)
  - ACTIVE → "Pause" + "Revoke" (with reason modal)
  - PAUSED → "Resume" + "Revoke"
- Detail view also shows metrics
- Summary dashboard at `/admin/promotions/summary` showing `GET /api/admin/promotions/metrics/summary`

### 3. Public placement rendering

For each placement, Vista calls the relevant `GET /api/promotions/{placement}` and renders the returned promotions on the matching page.

**Critical UX rule** — when rendering a promotion, fire:
- `POST /api/promotions/{id}/impression` ONCE per render (debounce — don't fire on every scroll)
- `POST /api/promotions/{id}/click` ON tap of the promotion card / link

Both endpoints accept anonymous calls. Pass any contextual info via `PromotionTrackRequest` body (check the DTO shape in `/v3/api-docs`).

**Visual disclosure** — the placement's UI label ("Featured" / "Sponsored") MUST appear prominently on each rendered promotion. Don't make sponsored content blend invisibly with organic.

### 4. Sponsor-side metrics

On `/promotions/{id}`:
- Fetch `GET /api/promotions/{id}/metrics`
- Render impressions, clicks, CTR
- Show a date-range picker if the backend supports it (check the DTO)

---

## How to fetch the canonical shapes

I deliberately did NOT inline every JSON body here because it would drift the moment the backend evolves. Always read the live spec:

- **Live API docs:** `https://haven.dreamhomes.today/v3/api-docs` (raw JSON) or `/scalar.html` (rendered)
- **DTO source** (if Scalar isn't enough): `src/main/java/com/dreamhomes/haven/promotion/dto/`
  - `CreatePromotionRequest` — POST body shape
  - `PromotionResponse` — sponsor-facing read shape
  - `PromotionPublicResponse` — public placement shape
  - `PromotionMetricsResponse` — per-promotion metrics
  - `PromotionMetricsSummaryResponse` — admin summary
  - `ApprovePromotionRequest`, `PromotionActionRequest`, `PromotionTrackRequest` — admin / tracking bodies

---

## Errors Vista should branch on

All errors follow Haven's standard RFC 7807 `application/problem+json` shape — branch on `status` first, then `type` URI suffix. Promotion-specific exceptions:

| Status | When | UI copy suggestion |
|---|---|---|
| 400 | Invalid date window (end before start), bad target type for placement | "Check your dates / target and try again" |
| 401 | Not authenticated on protected endpoint | (standard redirect-to-login) |
| 403 | Not the sponsor of this promotion / not an admin | (standard forbidden state) |
| 404 | Promotion ID doesn't exist | (standard not-found state) |
| 409 | Illegal state transition (e.g. try to pause a REVOKED one) | "This promotion is in <status> state — can't <action> it" |

---

## Migration / coordination notes for Vista

- **Adopt the word "Promotion"** everywhere user-facing. The older `ad/` module's "Ad campaign" naming is a separate, partially-superseded feature. If Vista was building screens against `MyAdCampaignsController`, those should be retired or migrated to Promotion.
- **Impression / click tracking is non-optional.** Without it the admin metrics dashboard shows zeros and sponsors can't see if their money is doing anything. Wire it on day one.
- **Public placement endpoints don't need auth** — fetch them on every public page render. Cache for a short TTL (~30s) if your component re-mounts a lot, but don't cache forever (an admin pause should reflect within a minute).

---

## Vista task queue entries to add

Suggest creating these in `docs/vista/vista-task-queue.md` so this becomes trackable:

- **VTASK-017** — Sponsor create + list + detail screens
- **VTASK-018** — Status badge component + lifecycle messaging
- **VTASK-019** — Admin moderation page + summary dashboard
- **VTASK-020** — Public placement renderers + impression/click tracking on all 3 placements
- **VTASK-021** — Migration / retirement of the older "ad campaign" UI (if any was built)

Each follows the standard VTASK template in `cursor-handoff-prompt.md`.
