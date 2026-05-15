# Temi — The First Timer

> *"I didn't even know where to start. The AI just walked me through everything."*

## Profile

| | |
|---|---|
| **Role** | Applicant |
| **Age** | 26 |
| **Location** | Lagos (Ikorodu → moving to VI / Yaba area) |
| **Background** | First real job at a fintech in VI; first time renting on her own. |

## The story

Temi just got her first real job at a fintech in VI and needs to move out of
her parents' house in Ikorodu. She has never rented a place before and has no
idea what caution fee means, what she should be checking at an inspection, or
whether ₦850,000 per year for a self-con in Yaba is fair. She opens
DreamHomes and starts chatting with Dream AI before she even creates an
account. It tells her the average for a self-con in Yaba is ₦700,000–₦900,000
so the price is on the high side but not outrageous. It explains what caution
fee is, what to look for during inspection, and shortlists three listings that
match her budget and commute. She creates an account, books two inspections,
and signs her first lease — all without calling a single strange number she
found on a wall.

## What she cares about

- **Education before commitment.** She wants to learn before she pays. The
  platform should never assume she knows the jargon.
- **Anonymous browsing.** She doesn't want to register before she's even
  decided what she's looking at.
- **Price sanity-checks.** Is this listing fairly priced for the area?
- **Trust signals on the listing AND the agent.** Verified property +
  verified agent + good reviews.
- **No surprise fees.** No inspection fee, no application fee.

## User stories

### Story 1 — Browse listings without an account ✅ Implemented

**As an** unauthenticated visitor
**I want to** browse and search published listings
**So that** I can decide whether to register.

**Acceptance criteria**
- [x] `GET /listings` is fully public — no auth required.
- [x] Returns paginated results with property summary (location, type, price).
- [x] Includes `Cache-Control: public, max-age=...` headers so CDNs / browsers can cache.
- [x] Sensitive owner / agent details are NOT included in the list view (only public profile fields).
- [x] Filterable by location, price range, type — verify in inventory.

**Endpoints involved**
- `GET /listings` (paginated, filterable)
- `GET /listings/{id}` (single listing detail with photos + slot availability)

---

### Story 2 — View a listing's photos + agent + reviews before deciding to engage ✅ Implemented

**As an** unauthenticated visitor
**I want to** see all photos, the assigned agent's profile + rating, the verified-property badge, and the open inspection slots
**So that** I can decide whether this is worth requesting an inspection for.

**Acceptance criteria**
- [x] `GET /listings/{id}` returns photos in display_order.
- [x] Listing detail includes assigned agent's user ID; following that to `GET /users/{id}/profile` returns rating + verified badges.
- [x] Open inspection slots visible without auth.
- [x] Verified-property badge surfaced if `Property.documentsVerifiedAt IS NOT NULL`.

**Endpoints involved**
- `GET /listings/{id}`
- `GET /listings/{id}/photos`
- `GET /listings/{id}/slots`
- `GET /listings/{id}/comments`
- `GET /listings/{id}/reviews` (reviews of past deals on this listing or similar — *confirm shape in inventory*)
- `GET /users/{id}/profile`

---

### Story 3 — Register as an applicant ✅ Implemented

**As a** convinced visitor
**I want to** create an APPLICANT account quickly
**So that** I can start engaging with listings.

**Acceptance criteria**
- [x] Registration with email + password + name; role defaults to `APPLICANT`.
- [x] Strict email validation.
- [x] Common-password blocklist enforced.
- [x] JWT issued immediately.

**Endpoints involved**
- `POST /auth/register`

---

### Story 4 — Submit applicant identity verification ✅ Implemented

**As an** applicant
**I want to** upload my NIN so my profile carries the verified-applicant badge
**So that** owners take my offers seriously.

**Acceptance criteria**
- [x] Submit `Verification` of type `APPLICANT_IDENTITY`.
- [x] Cannot submit while one is PENDING.
- [x] On approval, my user record's `identityVerifiedAt` stamps.

**Endpoints involved**
- `POST /verifications`
- *Admin*: `POST /admin/verifications/{id}/approve` / `/reject`

---

### Story 5 — Save a listing for later ✅ Implemented

**As an** applicant
**I want to** save a listing
**So that** I can come back to it from my saved collection.

**Acceptance criteria**
- [x] `POST /listings/{id}/save` creates a `ListingSave` (composite PK on user + listing).
- [x] Idempotent — saving twice doesn't error.
- [x] `DELETE /listings/{id}/save` un-saves.
- [x] My saved list is private to me.

**Endpoints involved**
- `POST /listings/{id}/save`
- `DELETE /listings/{id}/save`
- `GET /users/me/saves` *(confirm path in inventory)*

---

### Story 6 — Ask a question on a listing publicly ✅ Implemented

**As an** applicant who is curious about something not in the listing
**I want to** post a public comment / question
**So that** the owner or another applicant can answer where everyone can see.

**Acceptance criteria**
- [x] `POST /listings/{id}/comments` requires auth.
- [x] Comment is publicly readable.
- [x] I can soft-delete my own comment.
- [x] Owner / agent / admin can also soft-delete.
- [x] Comments thread (parent-child) is supported in the model.

**Endpoints involved**
- `POST /listings/{id}/comments`
- `GET /listings/{id}/comments` (public)
- `DELETE /comments/{id}`

---

### Story 7 — Book an inspection slot ✅ Implemented

**As an** applicant
**I want to** claim an open inspection slot
**So that** I can physically inspect the property.

**Acceptance criteria**
- [x] `POST /api/inspections` with `{ "slotId": <slotId> }` in the body.
- [x] Slot becomes unavailable to other applicants (partial UQ on `inspection_requests(slot_id) WHERE status IN (PENDING, APPROVED)`).
- [x] Owner + assigned agent get a Kafka-backed `INSPECTION_REQUESTED` notification.
- [x] I get a notification confirming.
- [x] I can cancel my own PENDING request via `DELETE /api/inspections/{id}`. Owner approval of the request is **not yet** a backend feature (see Amaka Story 9) — the request stays PENDING until the slot time arrives or I cancel.

**Endpoints involved**
- `GET /api/listings/{id}/slots` (find an open slot — public, no auth).
- `POST /api/inspections` (claim the slot).
- `GET /api/inspections/mine` (my requests).
- `DELETE /api/inspections/{id}` (cancel mine).

---

### Story 8 — Submit an offer ✅ Implemented

**As an** applicant who's done their inspection
**I want to** submit an offer at a price + terms
**So that** the owner can accept, decline, or counter.

**Acceptance criteria**
- [x] `POST /listings/{id}/offers` creates an `Offer` in `PENDING`.
- [x] Owner gets `OFFER_SUBMITTED` notification.
- [x] I can submit only one PENDING offer per listing at a time *(confirm in inventory)*.
- [x] If owner counters, a child offer arrives in my inbox.
- [x] I can ACCEPT, DECLINE, or COUNTER the counter.

**Endpoints involved**
- `POST /api/listings/{id}/offers`
- `PATCH /api/offers/{id}` (body: `{ "status": "ACCEPTED" | "DECLINED", "reason": "..." }` — same endpoint owner uses; service rejects "responding to your own proposal" with 403)
- `POST /api/offers/{id}/counter`

---

### Story 9 — Get notified of every action on my offer ✅ Implemented

**As an** applicant with offers in flight
**I want to** see every status change — accepted, declined, countered
**So that** I can react fast.

**Acceptance criteria**
- [x] Every state transition fires a `Notification` to the relevant party.
- [x] My inbox is paginated, filterable by read/unread.
- [x] Unread count exposed for badging the inbox icon.

**Endpoints involved**
- `GET /notifications/mine`
- `GET /notifications/unread-count`
- `PATCH /notifications/{id}/read`

---

### Story 10 — Leave a review after the deal closes ✅ Implemented

**As an** applicant whose offer was accepted and the deal closed
**I want to** leave a 1-5 star review with text on the owner / agent
**So that** future applicants see my experience.

**Acceptance criteria**
- [x] Review only allowed when listing is `CLOSED` and I had an `ACCEPTED` offer (gate enforced server-side).
- [x] Review references reviewer + reviewee + listing.
- [x] One review per (listing, reviewer, reviewee).
- [x] Reviewee's profile aggregate updates immediately.
- [x] I can soft-delete my own review.

**Endpoints involved**
- `POST /listings/{id}/reviews`
- `DELETE /reviews/{id}`

---

### Story 11 — Chat with Dream AI before / during / after browsing ⬜ Future

**As a** confused first-time renter
**I want to** ask "what's a fair price for a self-con in Yaba?" or "what should I check at inspection?" in natural language
**So that** I learn enough to make decisions confidently.

**Status**: Out of scope for this backend. The PRD reserves Dream AI as a forward-looking integration (LLM + structured market data). No endpoints yet.

## Journey through the platform

Temi's chronological flow:

1. **Anonymous browse** → `GET /listings` filtered by location.
2. **Open a listing detail** → `GET /listings/{id}` + photos + slots + agent profile.
3. **Save a couple to come back to** → register first → `POST /auth/register`, then `POST /listings/{id}/save` × 3.
4. **Submit applicant identity verification** → `POST /verifications`.
5. **Wait for Dayo's approval.**
6. **Pick a slot for the weekend** → `POST /api/inspections`.
7. **Inspection happens IRL.**
8. **Submit an offer at her budget** → `POST /listings/{id}/offers`.
9. **Get an OFFER_COUNTER notification — owner countered higher.**
10. **Counter back / accept / decline** → `POST /offers/{id}/counter` or `/respond`.
11. **Owner accepts.**
12. **Listing transitions to CLOSED.**
13. **Days later: leave a 5-star review** → `POST /listings/{id}/reviews`.

## Possible errors she encounters

| Scenario | HTTP | Body | UI guidance |
|---|---|---|---|
| Registers with weak / common password | `400` | Validation error on password | Inline form error. |
| Submits APPLICANT_IDENTITY when one already PENDING | `409` | `... "detail":"a pending verification of this type already exists"` | "Your previous submission is being reviewed." |
| Tries to claim an already-taken slot (race condition) | `409` | `... "detail":"slot already claimed"` | "Someone got that slot first — pick another." |
| Submits an offer on a CLOSED / PAUSED listing | `409` | `... "detail":"listing not open for offers"` | "This listing isn't accepting offers." |
| Submits a duplicate PENDING offer on the same listing | `409` | `... "detail":"another pending offer exists"` *(confirm in inventory)* | "You already have a pending offer here. Counter or decline first." |
| Tries to leave a review on a non-CLOSED listing | `409` | `... "detail":"listing is not closed"` | "Reviews open after the deal closes." |
| Tries to leave a duplicate review | `409` | `... "detail":"you have already reviewed this user on this listing"` | "You've already reviewed them." |
| JWT expired mid-form-fill | `401` | Standard | Save draft, redirect to login. |

## Test scenarios

### Golden path: browse → register → inspect → offer → close → review

```
1. Anonymous: GET /listings → assert public (no auth header), pagination works
2. Anonymous: GET /listings/{id} → assert detail + photos + slots + agent
3. Register Temi → assert role=APPLICANT, JWT issued
4. Submit APPLICANT_IDENTITY → assert PENDING
5. As Dayo, approve → assert badge stamped
6. Save listing → assert ListingSave row created
7. Claim a slot → assert InspectionRequest + notification to owner
8. Submit offer → assert PENDING + notification to owner
9. As owner, ACCEPT → assert offer ACCEPTED, listing eligible to close
10. As owner, mark CLOSED → assert state transition allowed
11. Submit review (5 stars, "great property") → assert ListingReview row + reviewee aggregate updated
```

### Edge cases

- Concurrent slot claim by two applicants → exactly one succeeds (200), other gets 409.
- Two PENDING offers on same listing → second rejected (or both allowed, depending on spec — confirm in code).
- Review attempt before listing CLOSED → 409.
- Saving the same listing twice → 200 (idempotent).
- Public browse pagination — verify `?page=2&size=20` works as expected.

### Public-cache header coverage

`PublicCacheHeadersIT` already covers this — every public discovery path
(`/listings`, `/listings/*`, `/users/*/profile`, etc.) carries `Cache-Control: public`.

## Related personas

- **[Amaka](amaka-the-lagos-landlord.md)** + **[Biodun](biodun-the-developer.md)** are the owners she'd offer to.
- **[Emeka](emeka-the-hustling-agent.md)** is the agent she'd negotiate with.
- **[Dayo](dayo-the-platform-guardian.md)** approves her identity verification.
- **[Ngozi](ngozi-the-skeptic.md)** is the *experienced* counterpart — same role, more scar tissue.
