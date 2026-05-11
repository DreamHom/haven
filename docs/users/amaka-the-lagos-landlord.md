# Amaka — The Lagos Landlord

> *"I just want to know my property is in good hands without having to take a bus to Lagos every month."*

## Profile

| | |
|---|---|
| **Role** | Owner (no agent) |
| **Age** | 41 |
| **Location** | Abuja (manages property in Lekki) |
| **Background** | Civil servant; inherited two flats from her late father. |

## The story

Amaka inherited two flats in Lekki from her late father. She doesn't live in
Lagos anymore and managing the properties remotely has been a nightmare —
fake agents, tenants who stop paying, and she never knows what's happening
with her property. She heard about DreamHomes from a colleague. She lists both
flats herself, uploads her C of O, and for the first time feels like someone is
actually accountable. She gets a notification every time someone requests an
inspection. She doesn't need an agent — she manages everything from her phone
in Abuja. DreamHomes gave her control she never had.

## What she cares about

- **Control without travel.** Every action should be doable from her phone.
- **Accountability.** When someone interacts with her listing — request,
  offer, inspection — she wants a paper trail.
- **Trust signals on her own profile.** The verified-owner badge so applicants
  don't think she's another scammer.
- **No middlemen taking fees.** She does this herself.

## User stories

### Story 1 — Register as an owner ✅ Implemented

**As an** unregistered owner
**I want to** create an account with the OWNER role
**So that** I can list properties and respond to inspection requests.

**Acceptance criteria**
- [x] Email + password registration with strict email validation.
- [x] Password rejected if it appears in the common-passwords blocklist.
- [x] Account is created with `Role.OWNER`.
- [x] Returns a usable JWT immediately on registration (no separate login step required for the first session).

**Endpoints involved**
- `POST /auth/register`

---

### Story 2 — Submit owner identity verification ✅ Implemented

**As a** registered owner
**I want to** upload my C of O / NIN as proof of identity
**So that** my profile carries the verified-owner badge that applicants trust.

**Acceptance criteria**
- [x] Submit a `Verification` of type `OWNER_IDENTITY` referencing my user ID.
- [x] Submission goes into `PENDING` status, queued for admin review.
- [x] Cannot submit a duplicate while one is `PENDING`.
- [x] On admin approval, my user record's `identityVerifiedAt` timestamp is stamped.
- [x] On rejection, I'm notified with the admin's reason and can resubmit.

**Endpoints involved**
- `POST /verifications`
- `GET /verifications/mine` (her view of pending + decided submissions)
- *Admin-side*: `POST /admin/verifications/{id}/approve` / `/reject`

---

### Story 3 — Register a property ✅ Implemented

**As a** verified owner
**I want to** create a Property record with address, type, and bedroom count
**So that** I can attach one or more listings to it later.

**Acceptance criteria**
- [x] Property created with type (`HOUSE` / `APARTMENT` / `LAND` / `COMMERCIAL`).
- [x] Address validated against minimum length.
- [x] Owner ID is set automatically from the JWT — Amaka cannot create a property "for" someone else.
- [x] Returns 201 + the property ID + a self-link.

**Endpoints involved**
- `POST /properties`

---

### Story 4 — List a flat without an agent ✅ Implemented

**As a** verified owner
**I want to** publish a listing for my property at a chosen price
**So that** applicants can discover and engage with it.

**Acceptance criteria**
- [x] Listing references one of my properties (rejected if the property isn't mine).
- [x] `ListingType` (RENT / SALE) and price required.
- [x] Listing starts in `OPEN` status (publicly browsable immediately).
- [x] Public discovery endpoint returns it within seconds (no admin approval needed for an OPEN listing — admin only takes down).

**Endpoints involved**
- `POST /listings`
- *Public*: `GET /listings` (paginated browse)

---

### Story 5 — Manage my listings (update / pause / close) ✅ Implemented

**As an** owner
**I want to** update price/description, mark a listing as PAUSED while I think, or CLOSED when the deal is done
**So that** I can reflect reality without deleting the listing's history.

**Acceptance criteria**
- [x] PUT updates only fields the owner is allowed to change.
- [x] Status transitions are state-machine validated (no jumping from CLOSED back to OPEN).
- [x] Cannot edit a listing I don't own (403).

**Endpoints involved**
- `PUT /listings/{id}`
- `GET /listings/mine` *(if exposed — confirm in inventory pass)*

---

### Story 6 — Be notified the moment someone requests an inspection ✅ Implemented

**As an** owner
**I want to** see a notification (and event row) the moment an applicant claims an inspection slot
**So that** I can prepare or be present.

**Acceptance criteria**
- [x] Inspection request triggers a `Notification` row of kind `INSPECTION_REQUESTED` for me.
- [x] Notification includes applicant ID + slot details.
- [x] Backed by the transactional outbox — never silently dropped.
- [x] Visible in `GET /notifications/mine` immediately.

**Endpoints involved**
- *Driven by*: `POST /listings/{id}/inspection-requests`
- *Surface*: `GET /notifications/mine`

---

### Story 7 — Receive and respond to offers ✅ Implemented

**As an** owner
**I want to** see offers as they arrive and accept / counter / decline them
**So that** I can close a deal on my terms.

**Acceptance criteria**
- [x] `OFFER_SUBMITTED` notification fires on every new offer.
- [x] Owner can ACCEPT, DECLINE, or COUNTER.
- [x] Accepting an offer transitions it to `ACCEPTED` (and any other PENDING offers on the same listing become locked from acceptance).
- [x] Counter creates a child offer (`parent_offer_id` chain).
- [x] State machine rejects illegal transitions (e.g. ACCEPTED → DECLINED) with 409.

**Endpoints involved**
- `POST /offers/{id}/respond` (accept / decline)
- `POST /offers/{id}/counter`
- *Driven from applicant side*: `POST /listings/{id}/offers`

---

### Story 8 — Open inspection slots ✅ Implemented

**As an** owner
**I want to** create date/time windows when applicants can inspect
**So that** I'm not negotiating times one-by-one over WhatsApp.

**Acceptance criteria**
- [x] Slot has start + end timestamp; end > start.
- [x] Cannot overlap an existing active slot for the same listing (DB-level GiST EXCLUDE constraint, not just app logic).
- [x] Slot is publicly visible to applicants browsing the listing.

**Endpoints involved**
- `POST /listings/{id}/slots`
- *Public*: `GET /listings/{id}/slots`

---

### Story 9 — Read and reply to comments on my listing ⬜ Future

**As an** owner
**I want to** answer public Q&A on my listing
**So that** applicants get answers they don't have to DM me for.

**Status**: Read works (commenting works for any authenticated user). Owner-reply *thread* is partial — the `Comment` model has a parent ID but there's no special owner-reply distinction yet. Worth tracking as a small enhancement.

---

## Journey through the platform

Amaka's chronological flow on a typical day:

1. **Register** → `POST /auth/register` → JWT in hand.
2. **Submit owner identity verification** → `POST /verifications` (type OWNER_IDENTITY).
3. **Wait for admin approval** → Dayo reviews, badge stamps on her profile.
4. **Create a property** → `POST /properties`.
5. **Publish a listing** → `POST /listings` referencing the property.
6. **Open inspection slots for the weekend** → `POST /listings/{id}/slots`.
7. **Day later**: applicant requests an inspection → notification arrives at her phone via `GET /notifications/mine`.
8. **Days later**: applicant submits an offer → another notification.
9. **She accepts** → `POST /offers/{id}/respond`.
10. **Deal closes**: she marks the listing CLOSED → `PUT /listings/{id}` with status update.
11. **Months later**: the applicant who closed leaves her a review → her profile rating updates.

## Possible errors she encounters

| Scenario | HTTP | Body shape (RFC 7807) | What the UI should show |
|---|---|---|---|
| Email already registered | `409` | `{ "type":"about:blank","title":"Conflict","status":409,"detail":"email already registered" }` | "An account with this email exists. Sign in instead." |
| Password too weak / common | `400` | Validation error response listing the failing field | Inline form error on the password field. |
| Trying to upload OWNER_IDENTITY when one is PENDING | `409` | `... "detail":"a pending verification of this type already exists"` | "Your previous submission is still being reviewed." |
| Trying to edit a listing she doesn't own | `403` | `... "detail":"forbidden"` | "You don't have permission to edit this listing." |
| Posting a slot that overlaps an existing one | `409` | `... "detail":"slot overlaps an existing active slot"` | "Pick a different time — that window is already open." |
| JWT expired mid-session | `401` | `... "detail":"unauthenticated"` | Redirect to login screen. |
| Creating a listing on a property she doesn't own | `403` | `... "detail":"not the property owner"` | (Unlikely from the UI, but the back-end rejects it defensively.) |
| Trying to ACCEPT an already-ACCEPTED offer on the same listing | `409` | `... "detail":"another offer on this listing was already accepted"` | "This listing already has an accepted offer." |

## Test scenarios

The integration tests should walk Amaka's golden path end-to-end. Suggested
coverage (most already exist in `src/test/java`):

### Golden path: solo-owner full journey

```
1. Register Amaka → assert JWT issued, role=OWNER
2. Submit OWNER_IDENTITY verification → assert PENDING
3. As Dayo, approve the verification → assert badge stamped on Amaka
4. Create a Property → assert ownerId == Amaka.id
5. Create a Listing for that Property → assert OPEN, publicly visible
6. Open an inspection Slot → assert visible on listing
7. As Temi, request the inspection → assert Amaka gets a NOTIFICATION
8. As Temi, submit an offer → assert Amaka gets another NOTIFICATION
9. Amaka accepts the offer → assert status ACCEPTED, other PENDING offers locked
10. Amaka marks listing CLOSED → assert state transition allowed
```

### Authorisation edge cases

- Amaka cannot edit a listing belonging to another owner → 403
- Amaka cannot create a Property "for" another user (her id is taken from the JWT, not the request body) → 201 with her id, not the requested id
- Amaka cannot ACCEPT an offer on a listing she doesn't own → 403

### Notification + outbox guarantees

- Inspection request writes both the inspection row AND an outbox row in the same transaction → assert via `OutboxEventRepository`
- Notification arrives even if the Kafka broker is briefly down (relay retries)
- Replaying the same Kafka event doesn't create duplicate notifications (event_id dedup)

## Related personas

- **[Temi](temi-the-first-timer.md)** is the most likely applicant on Amaka's listing — book inspections, submit offers, leave reviews.
- **[Dayo](dayo-the-platform-guardian.md)** processes Amaka's identity verification.
- **[Ngozi](ngozi-the-skeptic.md)** might also be the applicant — she'd care extra about Amaka's verified badge.
