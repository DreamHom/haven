# Biodun — The Developer Selling Off-Plan

> *"I build the homes. DreamHomes finds the people who deserve them."*

## Profile

| | |
|---|---|
| **Role** | Owner (with assigned agent) |
| **Age** | 47 |
| **Location** | Ibadan (selling units in Ojodu Berger, starting next project in Ibadan) |
| **Background** | Property developer; just completed a 12-unit apartment block. |

## The story

Biodun just completed a 12-unit apartment block in Ojodu Berger. He doesn't
have time to handle inquiries from 200 different people — he has another
project starting in Ibadan. He lists all 12 units on DreamHomes and assigns
Emeka as his agent. Emeka handles all the inspections, qualifies the serious
buyers from the browsers, and presents Biodun with shortlisted applicants
every Friday. Biodun approves or declines from wherever he is. Three units
are gone in the first month. He doesn't take a single inspection call.

## What he cares about

- **Volume listing.** 12 units in one place — adding them shouldn't be 12 manual repeats.
- **Delegation.** An agent should be able to do everything except sign final approvals.
- **Status visibility across many listings.** "Where are we on each unit?" — at a glance.
- **No inbound calls.** Every applicant communication routed through Emeka.

## User stories

### Story 1 — Register as an OWNER (no agent role) ✅ Implemented

Same flow as Amaka. Same endpoint. Different volume usage downstream.

**Endpoints involved**
- `POST /auth/register`

---

### Story 2 — Submit owner identity verification ✅ Implemented

Same as Amaka. The verified-owner badge is even more important for Biodun
because he's selling, not renting — applicants are committing larger sums.

**Endpoints involved**
- `POST /verifications`

---

### Story 3 — Register multiple properties (the apartment block as a parent + 12 units?) 🟡 Partial

**As a** developer
**I want to** model a 12-unit apartment block efficiently
**So that** I'm not duplicating shared address/owner info across 12 entries.

**Status**: The current `Property` model is one row per property, with no
parent/child concept. Biodun would create 12 individual Property rows today —
each with the same address but different unit number / floor / bedroom config.
Workable but verbose. Worth a future enhancement (composite property grouping)
if developer-scale usage becomes common.

**Endpoints involved**
- `POST /properties` × 12

---

### Story 4 — Publish 12 listings ✅ Implemented

**As a** developer
**I want to** publish a listing for each unit
**So that** applicants can browse and engage with each individually.

**Acceptance criteria**
- [x] Each listing references its property (one of the 12).
- [x] Each is `OPEN` immediately on publish.
- [x] Each listing's photos / price / description independent.
- [x] Bulk-create endpoint? *(not in current scope — currently 12 calls)*

**Endpoints involved**
- `POST /listings` × 12

---

### Story 5 — Assign Emeka as the agent on every listing ✅ Implemented

**As a** developer
**I want to** request a single trusted agent on all 12 listings
**So that** Emeka is the front door for every inquiry.

**Acceptance criteria**
- [x] One `POST /listings/{id}/agent-assignments` per listing, targeting Emeka's user ID.
- [x] Each creates an `AgentListing` in `REQUESTED`.
- [x] Emeka receives 12 notifications (or grouped — *to confirm*).
- [x] Emeka accepts each individually (or with a bulk-accept — *not currently exposed*).

**Endpoints involved**
- `POST /listings/{id}/agent-assignments` × 12

---

### Story 6 — Stay out of inspection scheduling ✅ Partially implemented (slot creation only)

**As a** developer with an assigned agent
**I want** the assigned agent to handle inspection slot creation and respond to incoming inspection requests
**So that** I'm not woken at 6am for an inspection booking.

**Acceptance criteria**
- [x] Once Emeka has accepted the assignment, he can `POST /api/listings/{id}/slots` on Biodun's listings.
- [x] Both Biodun AND Emeka receive notifications on inspection events (so Biodun can spot-check) — *Biodun isn't required to act*.
- [ ] **Agent (or owner) approves/declines a PENDING inspection request** — *no backend endpoint exists*. See Amaka Story 9 for the full picture; the inspection-response surface is genuinely pending on the server side. For now, neither Biodun nor Emeka can transition `InspectionRequest.status` away from `PENDING` (only the applicant can cancel their own).

**Endpoints involved**
- *Driven by Emeka*: `POST /api/listings/{id}/slots`.
- *Pending backend support*: an `agent-or-owner` approve/decline endpoint on inspection requests. Track alongside Amaka Story 9.

---

### Story 7 — Approve / decline shortlisted applicants every Friday ✅ Implemented

**As a** developer with limited time
**I want to** review the offers Emeka has shortlisted and accept / decline them in batch
**So that** I close deals on my schedule, not the applicant's.

**Acceptance criteria**
- [x] Each `Offer` is independent — Biodun can ACCEPT/DECLINE/COUNTER from his phone.
- [x] Notifications surface every offer; Biodun can scroll his inbox Friday morning and act.
- [x] Bulk-respond UX is a frontend concern; backend supports per-offer calls.

**Endpoints involved**
- `GET /api/notifications/mine` (filter client-side on `kind = OFFER_SUBMITTED`).
- `PATCH /api/offers/{id}` × N (body: `{ "status": "ACCEPTED" | "DECLINED", "reason": "..." }` — OWNER and APPLICANT share this endpoint; service rejects "responding to your own proposal" with 403).
- `POST /api/offers/{id}/counter` × N (creates a child offer; parent → `COUNTERED`).

---

### Story 8 — See dashboard of all 12 listings' status ⬜ Pending backend support

**As a** developer
**I want to** see a single view: per listing, status (OPEN/PAUSED/CLOSED), open offers count, scheduled inspections this week
**So that** I know where the project stands without three round-trips per row.

**Current state on the backend**
- `GET /api/listings/mine` returns Biodun's listings — paginated, OWNER-gated, **works**.
- `GET /api/offers/mine` returns offers where Biodun is either applicant *or* listing owner — **works**, but doesn't roll up per listing; the frontend has to fan-out + group.
- `GET /api/listings/{id}/slots` returns inspection slots on one listing — public, **works**.
- **There is no single rollup endpoint** that returns `(listingId, status, openOffersCount, scheduledInspectionsThisWeek, savesCount, viewCount)` in one shot. `viewCount` is on the listing row already (cheap); the rest would have to be aggregated server-side.

**What the frontend should do until this lands**
- Fan-out: 1 call to `GET /api/listings/mine`, then 1 call to `GET /api/offers/mine` grouped client-side by `listingId`, then 1 call per listing to `GET /api/listings/{id}/slots` (or skip slots in the dashboard view entirely).
- The `viewCount` field on each `ListingResponse` is already aggregated — render that directly. No second call needed.

**What the eventual contract probably looks like** *(not built yet — design sketch)*
- `GET /api/listings/mine?include=engagement` returning each row augmented with `openOfferCount`, `pendingInspectionCount`, `saveCount` — one DB query with a couple of LEFT JOINs + GROUP BY. Indexed paths already exist for the underlying counts.

---

### Story 9 — Revoke the agent if the relationship sours ✅ Implemented

Same as Story 8 in Emeka's persona, viewed from Biodun's side.

**Endpoints involved**
- `POST /agent-assignments/{id}/revoke`

## Journey through the platform

Biodun's chronological flow:

1. **Register as OWNER + submit identity verification.**
2. **Wait for Dayo to approve.**
3. **Create 12 properties** → `POST /properties` × 12 (current state — bulk endpoint future).
4. **Publish 12 listings** → `POST /listings` × 12.
5. **Request Emeka on all 12** → `POST /listings/{id}/agent-assignments` × 12.
6. **Emeka accepts each.**
7. **Biodun goes back to Ibadan and starts the next project.**
8. **Every Friday: scroll notifications, accept the offers Emeka has clearly endorsed, decline / counter the rest.**
9. **Three units close in the first month** → 3× `PATCH /api/offers/{id}` with `{ "status": "ACCEPTED" }` + 3× `PATCH /api/listings/{id}` (status → CLOSED).
10. **Months later: applicants leave reviews on Biodun + Emeka.**

## Possible errors he encounters

| Scenario | HTTP | Body | UI guidance |
|---|---|---|---|
| Trying to assign an agent who isn't a verified AGENT | `400` | `... "detail":"target user is not an agent"` | "That user can't be assigned as an agent." |
| Trying to assign an agent to a listing he doesn't own | `403` | `... "detail":"forbidden"` | (Defensive — UI shouldn't expose.) |
| Re-requesting Emeka while a PENDING request exists | `409` | `... "detail":"a pending invite already exists for this listing+agent"` | "You've already invited this agent on this listing." |
| Assigning a second agent while one is ACCEPTED | `409` | `... "detail":"listing already has an active agent"` | "Revoke the current agent first." |
| Accepting an offer on a listing he doesn't own | `403` | Standard | (Defensive.) |
| Accepting offer 1 then offer 2 on the same listing | `409` on offer 2 | `... "detail":"another offer on this listing was already accepted"` | "An offer here was already accepted." |

## Test scenarios

### Golden path: developer + agent multi-listing flow

```
1. Register Biodun (OWNER) + Emeka (AGENT) + verify both via Dayo
2. Biodun creates 3 Properties (test scaffold; full 12 not needed for ITs)
3. Biodun creates 3 Listings
4. Biodun requests Emeka on all 3 → 3× AgentListing.REQUESTED + 3 notifications
5. Emeka accepts each → 3× AgentListing.ACCEPTED + 3 notifications back to Biodun
6. As Temi/Ngozi (applicants), submit offers on each listing
7. Friday: Biodun acts on each offer (mix of ACCEPT, DECLINE, COUNTER)
8. Verify: only one offer per listing can be ACCEPTED; others auto-locked or remain PENDING per spec
```

### Authorisation + ownership

- Biodun cannot assign an agent on a listing belonging to another owner → 403.
- Biodun cannot accept an offer on a listing he doesn't own → 403.
- After REVOKING Emeka and re-requesting a different agent, the new agent's actions on the listing are authorised; Emeka's are not → 403.

### Concurrency

- Two applicants submit offers simultaneously → both succeed (PENDING), Biodun chooses.
- Biodun and Emeka simultaneously try to publish a slot for overlapping windows → exactly one succeeds (DB constraint catches the other) → 409.

## Related personas

- **[Emeka](emeka-the-hustling-agent.md)** is the agent Biodun delegates to.
- **[Amaka](amaka-the-lagos-landlord.md)** is the *anti-Biodun* — solo, hands-on, no agent.
- **[Temi](temi-the-first-timer.md)** + **[Ngozi](ngozi-the-skeptic.md)** are the buyers / renters whose offers Biodun would receive.
- **[Dayo](dayo-the-platform-guardian.md)** approved Biodun's owner identity (and Emeka's agent credentials).
