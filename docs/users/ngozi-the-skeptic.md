# Ngozi — The Skeptic Turned Believer

> *"For the first time, I felt like someone was actually on my side."*

## Profile

| | |
|---|---|
| **Role** | Applicant (rent-to-buy intent) |
| **Age** | 34 |
| **Location** | Lagos |
| **Background** | Married, one child. Burned three times before by fake agents who took inspection fees for already-rented properties. Sworn off property platforms for two years. |

## The story

Ngozi was burned three times — paid inspection fees for a house that turned
out to already be rented. She swore off property searches for two years. Her
husband convinces her to try DreamHomes. No inspection fees. Agent profile
shows 31 closed deals and a 4.6 rating. The listing has a verified-property
badge. She requests an inspection, the agent responds in 40 minutes, they
visit the property and it exists exactly as listed. She's not buying yet —
she's checking if Moniepoint financing could help. DreamHomes is the first
platform that made her feel like the system wasn't designed to cheat her.

## What she cares about

- **Trust signals so visible they're impossible to fake.** Verified property
  badge, verified agent badge, agent rating + closed deal count, review history.
- **No inspection fees, ever.** A platform that charges to look at a property
  is a scam by design.
- **Listing accuracy.** What's on the screen must match what's at the address.
- **Speed of agent response.** A scammer wouldn't respond in 40 minutes
  through a tracked channel.
- **A way to flag bad actors.** If she's burned again, the platform must
  hear it.

## User stories

### Story 1 — Browse anonymously, scrutinise trust signals ✅ Implemented

**As a** burned applicant
**I want to** see verified-property and verified-agent badges before I create an account
**So that** I can decide whether to engage at all.

**Acceptance criteria**
- [x] `GET /listings/{id}` returns the listing's owner / agent profile data needed to display badges.
- [x] `GET /users/{id}/profile` returns: `identityVerifiedAt`, `agentCredentialVerifiedAt`, `averageRating`, `reviewCount`, `closedDealCount`.
- [x] Property's `documentsVerifiedAt` surfaced on the listing detail.
- [x] All public, no auth required.

**Endpoints involved**
- `GET /listings/{id}`
- `GET /users/{id}/profile`
- `GET /users/{id}/reviews`

---

### Story 2 — Read past reviews on the agent ✅ Implemented

**As a** skeptical applicant
**I want to** read the actual text of past reviews on this agent
**So that** I can judge them in context, not just by aggregate score.

**Acceptance criteria**
- [x] `GET /users/{id}/reviews` returns the visible (non-soft-deleted) review list.
- [x] Each review shows reviewer name, listing reference, rating, text, date.
- [x] Public — no auth required.

**Endpoints involved**
- `GET /users/{id}/reviews`

---

### Story 3 — Request an inspection without paying a fee ✅ Implemented

**As an** applicant who's been burned
**I want** the platform to charge me zero up front for an inspection
**So that** I can verify the listing without losing money.

**Acceptance criteria**
- [x] No payment endpoint involved in the inspection request flow.
- [x] `POST /api/inspections` is free at the protocol level.
- [x] No "inspection fee" field anywhere in the API surface.

**Endpoints involved**
- `POST /api/inspections`

---

### Story 4 — Get a fast acknowledgement from the agent ✅ Implemented

**As an** applicant
**I want** the agent to receive my inspection request immediately
**So that** the response time on their profile is real, not a fake number.

**Acceptance criteria**
- [x] The instant the request is recorded, a `Notification` of kind `INSPECTION_REQUESTED` lands in the agent's (and owner's) inbox.
- [x] Backed by the transactional outbox — no silent drops.

**Endpoints involved**
- *Driven by*: `POST /api/inspections`
- *Agent surface*: `GET /notifications/mine`

---

### Story 5 — Submit an offer with intent (rent / buy / rent-to-buy) 🟡 Partial

**As an** applicant exploring rent-to-buy
**I want to** signal my intent (rent, outright buy, rent-to-buy financing) on my offer
**So that** the owner / agent can route me appropriately.

**Status**: The current `Offer` model has price + terms but no explicit
`intent` enum (`RENT` / `BUY` / `RENT_TO_BUY`). Workable today via free-text
terms, but a structured field would be clearer. Worth a future enhancement
when the Moniepoint financing integration lands.

**Endpoints involved**
- `POST /listings/{id}/offers` (current shape)

---

### Story 6 — Leave a public review after the deal ✅ Implemented

**As an** applicant whose offer was accepted and listing closed
**I want to** publicly review the agent and owner I worked with
**So that** the next Ngozi has even more signal.

**Same as Temi's Story 10.**

**Endpoints involved**
- `POST /listings/{id}/reviews`

---

### Story 7 — Report a listing that turns out to be a scam ⬜ Future

**As an** applicant who suspects a listing is fraudulent
**I want to** report it to the trust & safety team
**So that** Dayo can investigate and take it down.

**Status**: No `POST /listings/{id}/report` endpoint exists yet. Dayo can
take down listings reactively (`POST /admin/listings/{id}/takedown`), but
there's no user-facing report flow feeding into a moderation queue.
**This is a real gap** for Ngozi's persona. Worth scoping as a backlog item.

---

### Story 8 — Moniepoint financing integration ⬜ Future

**As an** applicant exploring rent-to-buy
**I want to** check whether Moniepoint financing could cover the deposit / monthly
**So that** I know whether to pursue the offer.

**Status**: Out of scope for this backend. PRD reserves Moniepoint financing
as a forward-looking integration. No endpoints yet.

## Journey through the platform

Ngozi's deeply skeptical chronological flow:

1. **Anonymous browse with intent to *not* engage** → `GET /listings`.
2. **Spots a listing** → `GET /listings/{id}` → checks for verified-property badge.
3. **Clicks through to agent profile** → `GET /users/{agent_id}/profile` → reads rating + closed deal count.
4. **Reads agent reviews** → `GET /users/{agent_id}/reviews` → looks for any 1-2 star outliers and reads the text carefully.
5. **Decides to engage** → registers → `POST /auth/register`.
6. **Submits applicant identity verification** (so her offer carries the badge) → `POST /verifications`.
7. **Books an inspection** → `POST /api/inspections`.
8. **Agent responds in 40 minutes** (notification timestamp visible).
9. **Inspection happens. Property exists. Listing is accurate.**
10. **Submits an offer** → `POST /listings/{id}/offers`.
11. **Owner accepts.**
12. **Months later: leaves a 5-star review.**

## Possible errors she encounters

| Scenario | HTTP | Body | UI guidance |
|---|---|---|---|
| Tries to book a slot already taken | `409` | `... "detail":"slot already claimed"` | "Pick another slot." |
| Tries to engage with a listing taken down by admin | `404` or `409` *(confirm)* | Standard | "This listing is no longer available." |
| Tries to leave a review on a listing she didn't have an ACCEPTED offer on | `403` | `... "detail":"not a deal participant"` | "Reviews are only for closed deals you participated in." |
| Tries to submit an offer on a CLOSED listing | `409` | `... "detail":"listing not open for offers"` | Clear messaging. |

## Test scenarios

### Golden path: skeptical applicant validates trust before engaging

```
1. Setup: Listing exists, owner verified, property documents verified, agent verified with 5 reviews + 4.6 avg
2. Anonymous: GET /listings/{id} → assert property badge field populated
3. Anonymous: GET /users/{agent_id}/profile → assert verified badges + rating
4. Anonymous: GET /users/{agent_id}/reviews → assert paginated reviews returned
5. Register Ngozi → assert role=APPLICANT
6. Submit APPLICANT_IDENTITY → assert PENDING
7. As Dayo, approve → badge stamped
8. Book inspection → assert agent gets notification within milliseconds (use OutboxRelay's after-commit hook)
9. Submit offer → assert PENDING
10. As owner, accept → assert ACCEPTED
11. As owner, mark CLOSED
12. Submit 5-star review → assert reviewee aggregate rises
```

### Trust-signal coverage

- An unverified agent's profile shows badge fields but with null timestamps.
- Property without documents verified does not falsely report a verified badge.
- Soft-deleted reviews are excluded from the public review list AND the aggregate.

### Listing taken down (admin action)

- After Dayo runs `POST /admin/listings/{id}/takedown`, Ngozi's `GET /listings/{id}` returns 404 (or marker — *confirm*).
- New offers on a taken-down listing: rejected with 409.

## Related personas

- **[Temi](temi-the-first-timer.md)** is the same role with no scar tissue. Both
  paths must work, but Ngozi is the harder-to-win user — design for her.
- **[Dayo](dayo-the-platform-guardian.md)** is who Ngozi *needs* to be doing
  his job rigorously. Every approved badge is a promise to her.
- **[Emeka](emeka-the-hustling-agent.md)** is the agent whose 40-minute
  response time + 4.6 rating earned Ngozi's trust.
