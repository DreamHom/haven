# Emeka — The Hustling Agent

> *"People used to ask me 'do you have an office?' Now they just look at my profile."*

## Profile

| | |
|---|---|
| **Role** | Agent |
| **Age** | 29 |
| **Location** | Port Harcourt |
| **Background** | Solo real estate hustler — phone, bike, reputation. No office. |

## The story

Emeka runs a small real estate business in Port Harcourt with no office — just
his phone, his bike, and his reputation. He's good at his job but loses
clients to bigger agencies because he looks informal. On DreamHomes his
verified badge, his 4.8 rating, and his 23 closed deals speak for him before
he even picks up the phone. He manages 7 active listings simultaneously, sends
post-inspection updates to owners without them calling to ask, and his
response rate sits at 94%. DreamHomes didn't change how he works — it just
made his work visible.

## What he cares about

- **Visibility of his track record.** Verified badge + rating + closed-deal
  count + response rate — every signal that says "I'm legitimate."
- **Concurrent listing capacity.** He needs to manage 7+ listings without losing track.
- **Owner trust.** When an owner is deciding between him and a no-name agent,
  the platform's signals should tip the call.
- **Speed.** Faster response = better rating = more listings.

## User stories

### Story 1 — Register as an agent ✅ Implemented

**As an** unregistered agent
**I want to** create an account with the AGENT role and provide my license number
**So that** I can be assigned to listings.

**Acceptance criteria**
- [x] Registration takes role + (when role=AGENT) `licenseNumber`.
- [x] Account created with `Role.AGENT` + `AgentProfile` row keyed on `userId`.
- [x] Returns JWT + the agent's user ID.

**Endpoints involved**
- `POST /auth/register`

---

### Story 2 — Submit agent credential verification ✅ Implemented

**As an** agent
**I want to** upload my real estate credentials for admin review
**So that** the verified-agent badge appears on my profile.

**Acceptance criteria**
- [x] Submit `Verification` of type `AGENT_CREDENTIALS` referencing my user ID.
- [x] Cannot submit a duplicate while one is `PENDING`.
- [x] On approval, my `agent_profiles.credential_verified_at` timestamp is stamped.
- [x] On rejection, I'm notified with the admin's reason.

**Endpoints involved**
- `POST /verifications`
- *Admin*: `POST /admin/verifications/{id}/approve` / `/reject`

---

### Story 3 — Be requested as the agent on a listing ✅ Implemented

**As a** verified agent
**I want to** receive a request from an owner who wants me to manage their listing
**So that** I can accept and start representing them.

**Acceptance criteria**
- [x] When an owner requests me, an `AgentListing` row is created with status `REQUESTED`.
- [x] I get a notification of kind `AGENT_ASSIGNMENT_REQUESTED`.
- [x] I see the listing details + the owner's reason for choosing me.
- [x] Pending invite is unique per (listing, agent) pair (DB-level partial unique index).

**Endpoints involved**
- *Driven by owner*: `POST /listings/{id}/agent-assignments`
- *My surface*: `GET /agent-assignments/mine`
- *Notification*: `GET /notifications/mine`

---

### Story 4 — Accept or decline an assignment ✅ Implemented

**As an** agent
**I want to** accept or decline a pending assignment request
**So that** I only commit to listings I can serve.

**Acceptance criteria**
- [x] Accept transitions `AgentListing` to `ACCEPTED`. Listing now has me as its assigned agent.
- [x] Owner gets a notification on either decision.
- [x] Cannot accept twice (state machine).
- [x] Cannot accept an assignment for someone else's user ID (403).

**Endpoints involved**
- `POST /agent-assignments/{id}/accept`
- `POST /agent-assignments/{id}/decline`

---

### Story 5 — Manage multiple active listings ✅ Implemented

**As an** active agent
**I want to** see every listing I'm currently assigned to
**So that** I can prioritise my day.

**Acceptance criteria**
- [x] One endpoint lists all my `AgentListing` rows where status=`ACCEPTED`.
- [x] Each entry shows the listing summary, owner ID, and the date I accepted.
- [x] Sorted by acceptance date desc.

**Endpoints involved**
- `GET /agent-assignments/mine` filtered by status

---

### Story 6 — Open + run inspections on behalf of an owner ✅ Implemented

**As the** assigned agent on a listing
**I want to** open inspection slots and respond to inspection requests
**So that** the owner doesn't have to take every call.

**Acceptance criteria**
- [x] Both owner AND assigned agent are authorised on the listing's slot endpoints.
- [x] Inspection request notifications fan out to **both** owner + assigned agent.
- [x] Slot overlap rules apply to me as they do to the owner.

**Endpoints involved**
- `POST /listings/{id}/slots`
- `GET /listings/{id}/slots`

---

### Story 7 — Be reviewed after a deal closes ✅ Implemented

**As an** agent
**I want** the applicant who closed the deal to be able to leave me a review
**So that** my rating reflects every successful transaction.

**Acceptance criteria**
- [x] Review can only be left when the listing is `CLOSED` and the reviewer had an `ACCEPTED` offer (gate enforced at write).
- [x] Review row references reviewer + reviewee + listing.
- [x] One review per (listing, reviewer, reviewee) — duplicate rejected with 409.
- [x] My profile's `averageRating` and `reviewCount` aggregate update on every write/delete.
- [x] Soft-delete supported (review hidden from public, count adjusted).

**Endpoints involved**
- *Driven by applicant*: `POST /listings/{id}/reviews`
- *My profile surface*: `GET /users/{id}/profile`
- *My reviews list*: `GET /users/{id}/reviews`

---

### Story 8 — Be revoked by an owner ✅ Implemented

**As an** assigned agent
**I want** the owner to be able to revoke me from a listing if the relationship sours
**So that** the owner stays in control even after they've delegated.

**Acceptance criteria**
- [x] Owner can revoke an `ACCEPTED` assignment.
- [x] Status transitions `ACCEPTED` → `REVOKED`.
- [x] I receive a notification.
- [x] I can no longer manage that listing.
- [x] Owner can re-request a different agent.

**Endpoints involved**
- *Driven by owner*: `POST /agent-assignments/{id}/revoke`

---

### Story 9 — Response-rate / closed-deals stats on profile ⬜ Future

**As an** agent
**I want** my profile to surface response rate, closed deal count, average response time
**So that** owners and applicants see the full picture, not just my star rating.

**Status**: `closedDealCount` derivable from `ListingReview` count where reviewer was an applicant on a CLOSED listing. Response rate / time would need new tracking. Worth a future enhancement if we expose it on `GET /users/{id}/profile`.

## Journey through the platform

Emeka's flow on a typical week:

1. **Register as AGENT with license number** → `POST /auth/register`.
2. **Submit AGENT_CREDENTIALS verification** → `POST /verifications`.
3. **Wait for Dayo to approve** → badge stamps; profile is now public-trustworthy.
4. **Owner Biodun requests him on Listing #42** → notification arrives.
5. **He accepts** → `POST /agent-assignments/{id}/accept`.
6. **Opens 3 inspection slots for the weekend** → `POST /listings/42/slots`.
7. **Applicants book** → he gets each notification, prepares.
8. **Days later**: applicant submits an offer → he reviews + relays to Biodun.
9. **Biodun accepts** → listing transitions to CLOSED.
10. **Applicant leaves a 5-star review** → his rating bumps up.
11. **Repeat across 7 active listings.**

## Possible errors he encounters

| Scenario | HTTP | Body | UI guidance |
|---|---|---|---|
| Trying to register as AGENT without `licenseNumber` | `400` | Validation error on `licenseNumber` field | Inline form error. |
| Trying to accept an assignment that's already ACCEPTED or DECLINED | `409` | `... "detail":"assignment already decided"` | "This invite was already responded to." |
| Trying to accept an assignment targeting a different agent | `403` | `... "detail":"not the targeted agent"` | (Defensive — UI shouldn't expose this path.) |
| Trying to open a slot on a listing he's not assigned to | `403` | `... "detail":"not authorised on this listing"` | "You're no longer the assigned agent on this listing." |
| Trying to open an overlapping slot | `409` | `... "detail":"slot overlaps an existing active slot"` | "Pick a different time." |
| Owner revokes him mid-flow | `403` on next mutation | `... "detail":"agent assignment is revoked"` | "Your assignment was revoked. Refresh." |
| Verification rejected | (notification, not HTTP) | Notification body includes admin reason | Inbox entry: "Your AGENT_CREDENTIALS submission was rejected: <reason>." |

## Test scenarios

### Golden path: agent assignment + closed deal

```
1. Register Emeka with role=AGENT + licenseNumber → assert AgentProfile created
2. Submit AGENT_CREDENTIALS verification → assert PENDING
3. As Dayo, approve → assert credentialVerifiedAt stamped
4. As Biodun (owner), request Emeka on Listing → assert AgentListing.REQUESTED + notification to Emeka
5. As Emeka, accept → assert ACCEPTED + notification to Biodun
6. As Emeka, open slot → assert visible on listing
7. As applicant, request inspection → assert notifications to BOTH Biodun and Emeka
8. As applicant, submit offer → assert notifications to BOTH
9. As Biodun, accept offer → listing moves to CLOSED
10. As applicant, leave 5-star review for Emeka → assert ListingReview row + Emeka's rating updated
```

### Concurrency edge cases

- Two owners both request Emeka on different listings simultaneously → both succeed.
- Owner requests Emeka while a previous PENDING request from another owner exists → both PENDING coexist (no listing-level lockout, only per-(listing,agent) lockout).
- Emeka accepts assignment 1; then assignment 2 on the same listing arrives from a different owner → ??? (currently allowed — listing's owner field is what matters; AgentListing is per-(listing, agent), so multiple agents could in theory be assigned to the same listing, though the partial unique index restricts the ACTIVE one to a single row).

### Authorisation

- Emeka cannot accept an assignment targeting a different agent → 403.
- After REVOKED, Emeka cannot open new slots, respond to offers, or accept new inspection requests on that listing → 403.

## Related personas

- **[Biodun](biodun-the-developer.md)** is the owner who hires Emeka — multi-listing developer relationship.
- **[Amaka](amaka-the-lagos-landlord.md)** is the *opposite* owner type — she explicitly does not want an Emeka.
- **[Dayo](dayo-the-platform-guardian.md)** approves Emeka's credentials and would suspend him if he took fees off-platform.
- **[Temi](temi-the-first-timer.md)** + **[Ngozi](ngozi-the-skeptic.md)** are the applicants whose reviews build Emeka's rating.
