# Biodun's review — Developer with 12 units in Ojodu Berger

I am 47. I just finished a 12-unit block on Plot 7 Berger Road in Ojodu. I
do not have time. My next site is breaking ground in Ibadan in three weeks.
DreamHomes was supposed to be the "billboard plus call-handler" so I do not
spend my days on the phone. Here is how it actually went.

---

## Setting up — onboarding for a developer with 12 units

I registered as OWNER, logged in, called `/me`, submitted my OWNER_IDENTITY
verification. Five requests, no real surprises. Same friction every other
persona will write up:

- ✅ `POST /auth/register` returned 202 cleanly.
- ⚠️ Register does not give me a token. I have to log in immediately after
  with the same credentials. For a tired man on a Saturday morning that is
  one extra step that earns the platform nothing.
- ⚠️ `POST /auth/login` returns just `{ "token": "..." }`. No userId, no
  role echo, no expiry. I have to make a separate `/me` call before I know
  what the system thinks of me.
- 🚫 No `GET /verifications/mine` or status-poll endpoint. After I submit
  my OWNER_IDENTITY my only way to check progress is to refresh the
  notifications inbox. For a busy man this is the opposite of how I want
  to be communicated with.
- 😕 `documentRefs` on the verification submission is just a free-form
  object — the OpenAPI schema is literally `additionalProperties: object`
  with zero hint about expected keys. I guessed `certificateOfOccupancy`,
  `nin`, `buildingApproval`. Dayo could reject me for wrong key names and
  I would have no idea what to fix.

### What would make this better for me as Biodun?
A "verification dashboard" — one GET that says: identity = APPROVED;
property A1 docs = PENDING; property A2 docs = NOT_SUBMITTED. And give
the doc-refs a proper schema so I am not guessing.

---

## The verification ritual — multiplied by every property

This is where the platform starts to forget who its users are.

- ❌ I have ONE Certificate of Occupancy that covers the entire 12-unit
  block — one plot, one C of O, one building approval, one developer.
  Yet the spec wants me to submit a `PROPERTY_DOCUMENTS` verification
  per property. That is 12 identical submissions, each linked to a
  different `propertyId`, each carrying the SAME C of O URL.
- ❌ Worse — Dayo (the admin) has to approve all 12 individually. I am
  burning my admin team's time as much as my own.
- 🚫 No "verify all properties under this Certificate of Occupancy" flow.
- 🚫 No "verify a development" concept at all — Property is the only unit
  of grouping and it sits alone.

In the audit I submitted property docs ONCE, for Unit A1, just to log the
PENDING row. I refuse to submit eleven more identical ones to make the
point. The point makes itself.

### What would make this better for me as Biodun?
A `Development` (or `Block`) entity that owns a single C of O and
auto-stamps the badge on every property under it. One submission, one
admin decision, twelve verified units.

---

## Adding 12 listings (or trying to)

I created 4 properties + 4 listings + 1 photo. That is 9 calls to model
roughly a third of the block. The full block is 27 calls before the agent
even sees it (12 properties + 12 listings + 1 verification + 1 photo +
maybe 1 register/login). That is too many.

- ✅ `POST /properties` and `POST /listings` work fine in isolation.
- ✅ Photo upload via multipart accepts a real JPEG and returns 201.
- ⚠️ Listing response says `"status": "LIVE"` but the OpenAPI description
  text and the example payload on `POST /listings` use the word `"OPEN"`.
  The schema enum says `LIVE / PAUSED / CLOSED`. So which one is it?
  I had to hit the live API to find out — `LIVE`. The spec contradicts
  itself. If I were writing a frontend against this I would have shipped
  a bug.
- 🚫 No bulk-create properties (`POST /properties/bulk`).
- 🚫 No bulk-create listings.
- 🚫 No "duplicate this property" / "duplicate this listing" call.
- 🚫 No "block" or "development" entity — every unit is a freestanding
  Property with nothing tying them together. So when applicants browse,
  Unit A1, A2, A3 from my block all look like independent listings from
  random sellers.
- 🚫 `POST /listings` has no `title`, no `description`, no `headline`, no
  occupancy / handover date, no "off-plan vs ready", no "promo" field.
  The browse list shows almost nothing to make my unit stand out.
- 🚫 No bulk photo upload. Real-world: my eight marketing photos are
  identical for all six 1-bed units. I would have to upload 48 photos
  one at a time.
- 😕 The address is a single 500-char string. I had to stuff "Unit A1"
  into it. There is no `unitNumber` field. So my 12 units differ only
  by the prefix of the address string and search/sort gets confused.

### What would make this better for me as Biodun?
1. `POST /properties/bulk` accepting an array.
2. A "duplicate property + listing" template call — hand it a property
   ID and how many copies, and it generates identical units with
   `unitNumber` increments.
3. Optional `description` / `headline` / `handoverDate` on listings so I
   can market.
4. A `Development` entity that gathers a block under one identity.

---

## Finding the right agent to delegate to — the showstopper

This is the single biggest finding in my walkthrough.

I want to invite Emeka (he is my agent in real life) onto all 12 listings.
The endpoint `POST /listings/{id}/agent-assignment` takes a body of
`{ "agentId": <integer> }`. I need Emeka's user ID.

**I have no way to find it.**

- 🚫 No `GET /users` with a search query.
- 🚫 No `GET /agents` directory.
- 🚫 No `GET /agents/by-license/{number}`.
- 🚫 No `GET /agents/by-name?q=Emeka`.
- 🚫 No autocomplete, no directory, nothing.

The only user-lookup in the entire spec is `GET /users/{id}/profile` —
which needs the ID I am trying to find. Catch-22.

In practice my options are:
1. Call Emeka, ask him to log in, ask him to call `/me` himself, and
   read his userId out to me over the phone. Demeaning for a paid
   professional. And I would do this for EVERY agent I ever consider.
2. Enumerate `/users/1/profile`, `/users/2/profile`, `/users/3/profile`,
   ... until I find one named "Emeka Okonkwo" with role AGENT and a
   credential-verified timestamp. That is a privacy disaster for the
   platform — anyone can scrape the entire user table — and I do not
   know if the IDs are even sequential.

Either way: a delegation-first product where the OWNER cannot find an
AGENT to delegate to is broken at the design level.

And once I have the ID:

- 🚫 No bulk-invite. I send 12 separate POSTs.
- 🚫 No "default agent on my profile" so future projects auto-invite him.
- 🚫 No way to revoke an agent across all my listings in one shot — I
  have to call `POST /agent-listings/{id}/revoke` 12 times if the
  relationship sours.
- 😕 The path is `POST /listings/{listingId}/agent-assignment` (singular)
  but the resource is `AgentListing` with a status field that includes
  REVOKED / DECLINED — meaning over a listing's life there can be many
  assignments. The path name lies about cardinality.

### What would make this better for me as Biodun?
1. `GET /agents?q=<name>&verified=true` — even if it only returns
   verified agents (so it doubles as a marketing nudge for agents to
   get verified).
2. `POST /listings/agent-assignments/bulk` — array of listingIds, one
   agentId, one notification to the agent: "Biodun has invited you to
   12 listings — accept all?"
3. `defaultAgentId` field on the owner profile so future listings
   auto-invite that agent on creation.

---

## Friday morning ritual — reviewing the week's offers

I went to Ibadan, sat with my coffee, and tried to triage offers.

- ✅ `GET /notifications/mine/unread-count` returned a number.
- ✅ `GET /notifications/mine` returned my inbox.
- 🚫 The unread-count is a single integer — it does not break down by
  type. As a developer I only care about offers; comments, agent acks,
  and saves are noise. I cannot tell from the count whether to bother
  opening the app.
- 🚫 No `GET /notifications/mine?type=OFFER_SUBMITTED`. My persona doc
  literally tells me to filter the inbox — the API has no filter. So I
  scroll the firehose looking for offers.
- 🚫 No `POST /notifications/mark-all-read` — only one-at-a-time.
  Twelve listings producing notifications all week = lots of tapping.
- 🚫 Notifications are not grouped by listing. If A1 has 4 offers I
  see 4 separate rows scattered across the timeline, not one grouped
  "A1 — 4 offers" item. At scale 12 this is unusable.

### What would make this better for me as Biodun?
A real "Friday inbox" view: filter by type, group by listing, show
counts per status. As a developer juggling 12 units I should be able
to open one screen and see "A1: 3 PENDING offers / B1: 1 PENDING / B2:
inspection requested" in 5 seconds.

---

## Finding offers to act on — second showstopper

I cannot find offers any way other than the notification feed.

- 🚫 No `GET /offers/mine` (offers I have received).
- 🚫 No `GET /listings/{id}/offers`.
- 🚫 No `GET /offers?listingId=...`.
- ⚠️ `GET /offers` does not exist either — `/offers` is POST-only.

So if I missed or accidentally marked-read an `OFFER_SUBMITTED`
notification, the offer is INVISIBLE to me. It exists in the database,
the applicant is waiting, and I have no way to discover its ID. The
only path back to it is for the applicant to nag me by some channel
outside DreamHomes ("hey, check the offer I sent — ID 42 I think").

For a developer with 12 listings and a flow of offers every day this
is genuinely scary.

### What would make this better for me as Biodun?
`GET /offers/mine?status=PENDING&listingId=...` with pagination. This
is table-stakes for any marketplace.

---

## Responding to offers

Once I had a (hypothetical) offer ID, the actions worked.

- ✅ `PATCH /offers/{id}` with `{ status: ACCEPTED | DECLINED }` — fine.
- ✅ `POST /offers/{id}/counter` — fine.
- ⚠️ The `RespondToOfferRequest` enum lists `PENDING / ACCEPTED /
  DECLINED / COUNTERED` as if all four are valid transitions via this
  endpoint. They aren't — `PENDING` cannot be set, and `COUNTERED` is
  a side-effect of `/counter`. The schema is broader than reality.
- 😕 No `reason` / `message` on a DECLINE. Cold for the applicant.
- 😕 `CounterOfferRequest` has no `currency` — implicitly inherits from
  parent? Spec doesn't say.
- ⚠️ ACCEPT does not auto-CLOSE the listing. I have to remember to
  `PATCH /listings/{id}` with `{ status: CLOSED }` myself. If I forget,
  Unit A1 stays "LIVE" and keeps receiving new offers nobody can accept.
- 🚫 No bulk-respond. Friday morning Emeka shortlists, say, 5 offers
  for me to ACCEPT and 8 to DECLINE — that's 13 separate API calls.

### What would make this better for me as Biodun?
- Auto-close the listing on ACCEPT (or at least respond with a banner
  the frontend can prompt the user to confirm).
- Bulk decline endpoint.
- Decline reason / message field.

---

## The dashboard I do not have

Not a single endpoint exists to ask "what do I own on this platform?"

- 🚫 `GET /listings/mine` — does not exist.
- 🚫 `GET /properties` (list mine) — does not exist; `/properties` is
  POST-only.
- 🚫 `GET /properties/{id}` — does not exist either. Once I create a
  property and forget to write down the ID returned, the property is
  effectively orphaned to me. There is no recovery path through the API.
- 🚫 `GET /listings/{id}` exists — but I need the ID. And it returns
  no engagement counters (offers, inspections, comments) — so even if
  I had the ID, I cannot tell from the listing detail "this one needs
  attention".

For a developer with 12 listings, no portfolio view + no per-listing
engagement counters means I cannot do my job from the app alone. I
will be back on WhatsApp asking Emeka, "boss what's happening with
A3 today?" — defeating the entire delegation premise.

### What would make this better for me as Biodun?
- `GET /listings/mine` with embedded `pendingOffersCount`,
  `scheduledInspectionsCount`, `viewsLast7d`.
- `GET /properties/mine`.
- A genuine "owner dashboard" composite endpoint.

---

## Cross-persona dependencies I had to log without resolving

These were honest BLOCKED states in my walk:

- I need Dayo to approve my OWNER_IDENTITY before I can show the badge.
- I need Dayo to approve A1's PROPERTY_DOCUMENTS before that listing
  shows the verified-property badge.
- I need Emeka to register, get verified, and tell me his userId before
  I can invite him.
- I need Emeka to call `accept` on each of my 12 invites before he can
  do any work on the listing.
- I need Temi / Ngozi to actually submit offers before I have anything
  to accept / decline.

All of these are normal cross-persona orchestration. None of them are
THE problem — the problem is that on every cross-persona handoff there
is no batching, no "do all of these at once" affordance.

---

## What worked well (credit where it's due)

- ✅ Register / login flow is simple, anti-enumeration register is the
  right call.
- ✅ JWT-based auth, `/me` is fast.
- ✅ Property + listing + photo upload all worked first time.
- ✅ Notification inbox returned promptly.
- ✅ The OWNER_IDENTITY and PROPERTY_DOCUMENTS submission paths were
  clear, even with the documentRefs ambiguity.
- ✅ Counter-offer chain (`parentOfferId`) is a clean model.
- ✅ Closing a listing via PATCH worked exactly as the schema said.

---

## Top 5 things I'd fix tomorrow

Ranked by impact on developer-scale users (anyone with more than 3
listings):

1. **Build a `GET /listings/mine` endpoint** with pendingOffersCount,
   scheduledInspectionsCount, status, and price per row. Without this
   no developer can use the platform without external tracking.
   Trivially simple to ship; gigantic UX win.
2. **Build a `GET /agents?q=...&verified=true` directory.** Without
   this, owners cannot delegate, which is THE feature that
   distinguishes this platform from Jiji. Right now delegation requires
   out-of-band coordination and the platform adds zero value.
3. **Build a `GET /offers/mine` endpoint.** Without this, a missed
   notification = a lost deal. This is a marketplace integrity issue,
   not a UX nicety.
4. **Bulk-create endpoints for properties, listings, agent-assignments.**
   `POST /properties/bulk`, `POST /listings/bulk`,
   `POST /agent-assignments/bulk`. Or at minimum a "template + count"
   flow. Twelve manual creates is the difference between a developer
   listing 12 units and a developer listing 3 and giving up.
5. **Fix the listing status enum mismatch (LIVE vs OPEN) in the spec
   and add a `description` / `headline` / `handoverDate` to listings.**
   The spec inconsistency will burn frontend integrators; the missing
   marketing fields make it impossible to differentiate an off-plan
   developer launch from a one-off resale.

Honourable mentions: a `Development` / `Block` parent entity (so one C
of O serves twelve units); a verification status query endpoint; an
auto-close-on-ACCEPT behaviour; a notification type filter.

---

I am going back to the site office in Ibadan now. Email me when the
dashboard exists.

— Biodun A.
