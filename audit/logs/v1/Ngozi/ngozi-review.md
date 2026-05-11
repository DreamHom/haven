# Ngozi's review of DreamHomes — a burned skeptic's nine days

> *I have been cheated by three property platforms in Lagos. I do not
> need a fourth. My husband talked me into trying this one. Here is
> exactly what I saw, what I felt, and where I would have closed the
> tab.*

**Run summary**: 23 requests, 31 / 31 assertions passed, full HTML report
at `audit/reports/ngozi.html`. Every request hit a real running server —
no mocks, no fakes.

---

## Day 1, 9:42pm — what I noticed before I would even sign up

I came in cold. No account, no email, just looking. Here is what I did
in order, with what I felt after each one.

> ✅ `GET /api/listings?page=0&size=10` → 200. The shape is what I
> expected: `content[]` and a `page` block. **But the array was empty.**
> Total elements: zero.

> ⚠️ `GET /api/listings?page=1&size=10` → 200. Still empty. There is
> nothing on this platform tonight. As a real person I would close the
> tab right here. The "platform" with no listings is just a login wall.

> ⚠️ `GET /api/listings/17` → 404 `application/problem+json` with the
> body `"detail":"Listing 17 was not found"`. Honest 404, properly
> shaped, RFC-7807 style. I appreciate that — when something doesn't
> exist the platform tells me so plainly. But the only reason I tried
> id=17 is because the **API documentation example uses it** — a real
> user wouldn't even have a number to type.

> ⚠️ `GET /api/listings/17/photos` → 200 with `[]`. Empty.

> ⚠️ `GET /api/listings/17/comments` → 200 with empty page. So the Q&A
> thread that I trust most as a buyer (because past Ngozis post warnings
> there) doesn't exist for this listing.

> ⚠️ `GET /api/listings/17/slots` → 200 with `[]`. Either no inspection
> slots are open or the listing is fake. I cannot tell the difference.
> A truly active listing should have *at least one* slot in the next
> two weeks.

### What would make this better for me as Ngozi?

The empty state is the biggest problem of Day 1. I came to this
platform skeptical and saw zero properties. **I would never have made
it to Day 3.** The product needs either (a) seeded sample listings on
first visit so a curious browser can see the trust signals at all, or
(b) a banner saying "we are launching in your area soon — leave your
email." Right now it is silent and that silence reads as broken.

Also: the OpenAPI spec invites me to look at listing id=17, but in a
fresh database that ID does not exist. Either the example IDs in the
spec should be plain placeholders that match seeded data, or the spec
should say "look up an ID from the listings index first."

---

## Looking for the verified-property badge — the single thing I came for

This is the badge that decides whether I engage. My persona doc says
acceptance criteria #3 for Story 1 is: *"Property's `documentsVerifiedAt`
surfaced on the listing detail."* Let me check.

> 🚫 The `ListingResponse` schema in the OpenAPI spec lists `id`,
> `propertyId`, `ownerId`, `listingType`, `askingPrice`, `currency`,
> `cautionFee`, `serviceCharge`, `agencyFee`, `status`, `approvedAt`,
> `viewCount`, `createdAt`, `updatedAt`, `property`. **There is no
> `documentsVerifiedAt` field anywhere — not on the listing, not on the
> nested `PropertySummary` (the latter I cannot see exhaustively from
> the spec but it is not surfaced in any documented example).**

> 🚫 The listing response also has no `agentId` separate from `ownerId`,
> no nested agent block, no agent rating snapshot. So when I open a
> listing card I cannot answer "who would I actually be dealing with
> here, and are they trusted?" without making a second call to
> `/users/{ownerId}/profile` and inferring everything.

This is the core failure for me. The whole reason a verified-property
badge exists is so a skeptic like me can spot it on the listing card
**without clicking through anywhere**. If it is not on the listing
detail and not on the listing index, it does not exist for my eyes,
and I treat every listing as unverified by default — exactly like the
last three platforms that burned me.

### What would make this better for me as Ngozi?

Add `documentsVerifiedAt` (and ideally a denormalised `agentVerifiedAt`,
`agentAverageRating`, `agentReviewCount`) directly on the listing card
in `GET /api/listings`. The badge has to be on the SERP, not behind
two more clicks. Today it is behind zero clicks because it does not
exist anywhere I can see.

---

## Investigating the agent before I would ever call them

I went looking at the only owner I could find an ID for (the spec
example shows `id: 7` for "Amaka Okafor", an OWNER).

> ⚠️ `GET /api/users/7/profile` → 200. The body:
> ```json
> {"id":7,"fullName":"Amaka Okafor","displayName":"Amaka O.",
>  "role":"OWNER","identityVerifiedAt":null,
>  "agentCredentialVerifiedAt":null,"suspended":false,
>  "averageRating":null,"reviewCount":0,
>  "joinedAt":"2026-05-11T10:10:40.194651Z"}
> ```
> Every trust signal I would scrutinise is null or zero: identity not
> verified, no rating, no reviews, joined less than an hour ago.

> 🚫 **There is no `closedDealCount` field.** My persona doc explicitly
> says "agent's 31 closed deals" is one of the three things I check
> first. The spec does not surface this anywhere — not on the user
> profile, not as a separate stat endpoint. The platform is asking me
> to trust on aggregate-rating-and-review-count alone, but a 5.0 rating
> from 2 reviews means nothing. 31 closed deals at 4.6 means everything.

> 🚫 **There is no agent response-time field.** My persona doc tells me
> I trust agents who respond in <40 minutes through tracked channels.
> The platform records inspection requests and notifications with
> timestamps internally — so it has the data — but it is not exposed
> on the public profile as "median response time."

> ✅ `GET /api/users/7/reviews?page=0&size=20` → 200. Empty page,
> shape is correct. The endpoint exists, it is public, no auth needed.
> When reviews start landing here, I will be able to read them in full.

The all-null profile is harmless on day one (it is a brand-new account)
but it tells me what the trust signals will look like even after a year
on the platform: **no closed-deal count, no response time, no badges
that aggregate everything into one trust score**. I will be left
squinting at a 4.6 rating and a review count and trying to guess how
much business this person has actually closed.

### What would make this better for me as Ngozi?

Three additions on `PublicUserProfile`:
1. `closedDealCount` — the number of `CLOSED` listings this user
   participated in (as owner, agent, or accepted-applicant).
2. `medianResponseMinutes` — the platform-measured median time from
   inspection-request to agent acknowledgement.
3. A consolidated `trustScore` band ("highly trusted / new / caution")
   that combines verified-identity, verified-credentials, deal count,
   response time, and rating, so a skeptic does not have to do the
   maths on five fields.

Also, the response surfaces `displayName` separately from `fullName` —
good, that respects privacy. But `joinedAt` being on the public profile
is great as a trust signal ("agent has been on platform 3 years" >
"joined yesterday"). Keep that.

---

## Trying to find a "report this listing" button

This is the moment that decides whether I believe DreamHomes is
designed for *me*.

> ✅ `POST /api/listings/17/report` (no auth) → 401, empty body. The
> endpoint exists. **This alone changed my opinion of the platform.**
> The previous platforms didn't even pretend to have a complaint
> channel. I tried it anonymously to confirm it was there before I
> bothered registering.

> ⚠️ The 401 response has **no body**, no `application/problem+json`
> envelope, no `detail` field saying "you must log in to file a
> report." Compare with the 404 on `/listings/17` which returned a
> proper Problem+JSON. So when an unauthenticated user accidentally
> hits this on a real frontend, the frontend has no message text to
> display except a generic "401 Unauthorized." That is missable. A
> real user might think the report button is broken when actually
> they just need to log in.

> ✅ The schema for the report payload includes a `reason` enum with
> `SCAM`, `OFF_PLATFORM_FEES`, `STALE_OR_TAKEN`, `INAPPROPRIATE_CONTENT`,
> `OTHER`. **The fact that `OFF_PLATFORM_FEES` exists as a first-class
> enum value made my eyes water.** Someone designed this for my exact
> scar tissue. This is the single best signal on the entire platform
> that the team understands the Lagos rental scam pattern.

> ⚠️ When I actually tried to report listing 17 logged in
> (`POST /api/listings/17/report` with `OFF_PLATFORM_FEES`), I got a
> 404 — but only because the listing genuinely doesn't exist. So I
> couldn't actually exercise the happy path. The 404 was well-shaped
> Problem+JSON, same as the GET — that's consistent.

### What would make this better for me as Ngozi?

The report channel existing is a 10/10 trust win. Three small fixes:

1. The 401 needs a body. Even just `{"detail": "Log in to file a
   report"}` — silent 401s look broken.
2. After a successful report, send the reporter a sync notification:
   `"We've received your report on listing X and a moderator will
   review it within 24 hours."` Without that, I have no idea whether
   the report went into a queue or a black hole. Right now my Day 9
   `/notifications/mine/unread-count` was 0 even after the duplicate
   report attempts — meaning I get nothing back from the system at all.
3. Surface a `reportCount` (or at least a "this listing has been
   reported" pill) on the listing detail so the next Ngozi sees
   the warning sign without having to file her own.

---

## Reluctantly registering — Day 3

> ✅ `POST /api/auth/register` → 202 Accepted. No body. The OpenAPI
> describes this as "anti-enumeration: the response is identical
> whether the email was newly registered or already taken." As a
> security-conscious skeptic, **I love this** — a scammer can't probe
> whether my email is on the platform.

> 😕 But as a confused new user, I just submitted a form and got back
> nothing. Did it work? Did the email already exist? I don't know. The
> spec promises me an email-confirmation link — I cannot test that
> from here, but the response should at least say
> `{"detail": "If this email is new, an account was created. Log in to
> continue."}`. A bare 202 is a UX dead end.

> ✅ `POST /api/auth/login` → 200 with `{"token": "<JWT>"}`. Worked.

> ⚠️ The OpenAPI example for login showed `tokenType: "Bearer"` and
> `expiresInSeconds: 3600`. **The actual response only contains
> `token`.** As a frontend dev I would have built code expecting
> `expiresInSeconds` and silently broken. As a user I now have no idea
> when my session expires. If I leave the tab open for two hours and
> come back, will my saved listing still be there?

> ✅ `GET /api/me` → 200, role correctly came back as `APPLICANT`.
> Good — the registration role wasn't silently swapped.

> ⚠️ But `/me` returned `{userId, email, role, tokenVersion}` — **no
> `fullName`**, even though the OpenAPI example explicitly shows
> `"fullName": "Amaka Okafor"` in the principal. So either the spec
> is wrong or the implementation is wrong; either way the contract
> doesn't match reality. As a user this means the app cannot greet me
> by name on app boot without a second `/users/{me}/profile` call.

### What would make this better for me as Ngozi?

The login flow itself works. Two clarifications would make it feel
finished:
1. Login response should include `expiresAt` (or `expiresInSeconds`),
   so the client can show me a "your session expires in 22 minutes"
   warning instead of just dropping me into a 401.
2. Either the spec example for `/me` should be updated to match
   reality (no `fullName`) or the endpoint should actually return it.
   Spec drift like this is exactly the kind of thing that erodes my
   trust if I'm a developer integrating with this API for an in-house
   tool.

---

## Submitting my identity — fair is fair

> ✅ `POST /api/verifications` with `type: APPLICANT_IDENTITY` → 201.
> Came back with `{"id": 1, "type": "APPLICANT_IDENTITY",
> "status": "PENDING", ...}`. Exactly what I expected. Status PENDING
> means a human will look at it.

> 🚫 **There is no file-upload endpoint to attach my actual NIN slip.**
> The schema asks for `documentRefs` as a free-form `Map<String,Object>`
> with **no validation, no required keys**, no example beyond the spec
> showing `{"kind": "C_OF_O", "ref": "..."}`. So I stuffed in a fake
> URL pointing nowhere. There is nothing in the contract telling me
> the URL must be reachable, must be HTTPS, must be a particular
> document type. I have no confidence the moderator will actually be
> able to view my NIN.

> 🚫 **There is no `GET /verifications/mine` endpoint** I can find in
> the spec. Once I submit, I cannot poll status from my side. I have
> to wait for a notification (which I might miss). On a phone with
> notifications turned off, I have no way to ask "did my verification
> go through yet?"

### What would make this better for me as Ngozi?

1. Multipart upload endpoint for verification documents (NIN slip,
   driver's licence, etc.) so I can submit a real photo, not a URL
   I have to pre-host somewhere I do not own.
2. `GET /verifications/mine` returning my submission history
   with current status. Without it, the verification feels like
   shouting into a void.
3. Constrain `documentRefs` in the schema — at minimum a
   discriminated union per `type`: `APPLICANT_IDENTITY` requires
   `kind: "NIN"` + `number: <11 digits>` + an optional file ref.
   Right now I could send `documentRefs: {"foo": "bar"}` and the
   contract accepts it.

---

## Booking an inspection — fee anywhere?

> ✅ `GET /api/listings/17/slots` → 200 (empty). The endpoint exists,
> public, no auth.

> ⚠️ `POST /api/inspections` with `{slotId: 12, notes: "..."}` → 404
> `application/problem+json` with `"detail":"Inspection slot 12 was not
> found"`. The error message is genuinely good — it tells me *which*
> resource was missing. As a skeptic, the **most important thing about
> this request was what was NOT in the request body or the response
> body: there was nowhere for me to enter a payment.** No `paidAt`, no
> `feeAmount`, no `paymentMethod`, no `paymentRef` field anywhere in
> the inspection request schema or response. **This is the single
> structural reason I would actually use this platform — the protocol
> itself doesn't have a slot for an inspection fee, so an agent can't
> demand one without going off-platform.** That is exactly what my
> persona doc said I needed (Story 3).

> ⚠️ I could not actually complete the inspection booking because
> there are no slots in the DB. This is a cross-persona dependency:
> an agent (Emeka) needs to open slots before I can claim one. Logged
> as a blocker; no fault of the API.

> 🚫 **No "did the agent see this?" feedback.** When I claim a slot
> the response is the inspection record (`status: REQUESTED`), but
> there is nothing telling me *when the agent will be notified* or
> *how to escalate if they don't reply in 24 hours*. My persona doc
> tells me I trust agents who respond in <40 minutes — without an
> "agent acknowledged at" timestamp on the inspection record I cannot
> measure that.

### What would make this better for me as Ngozi?

1. On `InspectionResponse`, add `agentAcknowledgedAt` and
   `agentRespondedWithinMinutes`. These are the numbers I literally
   look at to decide if the agent is real.
2. A "second-attempt" endpoint: if the agent hasn't acknowledged in
   N hours, let me ping. Right now I have no recourse.
3. The protocol-level absence of any payment field on inspections
   is the platform's biggest unspoken trust feature. **Make it
   spoken.** A banner on the listing card saying "Inspections are
   always free. If anyone asks for a fee, report them." would be the
   single most reassuring sentence a skeptic could read.

---

## Submitting my offer — can I express rent-to-buy intent?

> ⚠️ `POST /api/offers` with my rent-to-buy message → 404 (no listing
> 17 exists). The 404 shape is consistent.

> 🚫 The `SubmitOfferRequest` schema has `listingId`, `amount`,
> `currency`, `message`. **There is no `intent` enum** for
> `RENT / BUY / RENT_TO_BUY`. I had to cram my rent-to-buy plus
> Moniepoint-financing intent into the free-text `message` field
> and hope the owner reads it. My persona doc explicitly calls this
> out as Story 5 ("Partial — workable today via free-text"). Reality
> matches the spec, so this is a known gap, not a surprise.

> 🚫 The `OfferResponse` likewise has no `intent` field. So if the
> owner accepts my offer, there is no place in the offer record where
> "this is a rent-to-buy" is structured — only buried in the message.
> If the owner later forgets and tries to evict me at end-of-lease,
> there is no contract field to point at.

### What would make this better for me as Ngozi?

Add `intent: enum [RENT, BUY, RENT_TO_BUY]` (required) and
`financingPartner: enum [SELF, MONIEPOINT, OTHER, NONE]` (optional)
to both the request and response. Until rent-to-buy is a first-class
field, this platform cannot serve the use case my persona doc
describes — and that use case is most of why I would use the
platform at all.

---

## Saving listings, asking questions, leaving the platform open in a tab

> ⚠️ `POST /api/listings/17/save` → 404 (no listing). When listing 17
> existed I would expect a clean 204.

> ✅ `GET /api/saves/mine` → 200 with empty page. Endpoint works,
> shape is correct, paginated. **My saves are scoped to me** — that's
> the documented promise; I'll trust it.

> ⚠️ `POST /api/listings/17/comments` → 404 (no listing).

---

## My report goes through — Day 9, the moment of truth

> ⚠️ `POST /api/listings/17/report` (logged in, with
> `OFF_PLATFORM_FEES`) → 404. Listing doesn't exist.

> ⚠️ Re-tried (duplicate-report scenario). Also 404. So I never got
> to verify the documented 409 "duplicate report" behaviour. **This
> is critical to test once a real listing exists** — the duplicate
> behaviour is what stops me from spam-reporting in panic.

> ⚠️ `GET /api/notifications/mine/unread-count` → 200, **count = 0.**
> Across the whole nine-day journey — registering, submitting
> identity, attempting to claim a slot, attempting to file an offer,
> filing two reports — **the platform sent me zero notifications.**
> Verification submission alone should generate a "your verification
> is in the queue" sync notification.

### What would make this better for me as Ngozi?

The biggest specific missing piece is a **"system to user" sync
notification on every action I take.** Specifically:
- Verification submitted → "we got it, expect a decision within 24h"
- Inspection requested → "agent has been notified at HH:MM"
- Offer submitted → "owner has been notified at HH:MM"
- Report filed → "trust & safety has been alerted"

Right now my inbox is empty after nine days of activity. That is the
silence of a platform that doesn't talk back, and silence is what
scammers feel like.

---

## Errors that hurt vs errors that helped

| Endpoint | Status | Body | Helped me? |
|---|---|---|---|
| `GET /listings/17` | 404 | `application/problem+json` with `detail: "Listing 17 was not found"` | ✅ Yes — clear, named, I know what's missing |
| `POST /inspections` | 404 | Same Problem+JSON, names the slot | ✅ Yes |
| `POST /listings/17/report` (no auth) | 401 | **empty body** | ❌ No — silent 401 reads as broken |
| `POST /auth/register` | 202 | empty body | 😕 Sort of — anti-enum is good security but bad UX |

The 404 Problem+JSON pattern is a real win. Apply it to 401 and 403
too. Either every auth failure speaks or none of them do — mixed
silence is the worst case.

---

## Things that genuinely surprised me (positive)

1. **`OFF_PLATFORM_FEES` exists as a first-class report reason enum.**
   Not "OTHER", not buried in a textarea. A first-class enum value.
   Whoever wrote that spec knew about Lagos.
2. **No `feeAmount` field exists anywhere on inspection requests.**
   The protocol structurally cannot ask me for an inspection fee.
   That is design-as-trust.
3. **The 404 Problem+JSON shape is honest and named.** Most platforms
   return generic 404s; this one tells me *what* was missing.
4. **`POST /listings/{id}/report` exists at all.** My persona doc
   marked Story 7 as `⬜ Future` — meaning the persona writer didn't
   know the endpoint had been built. **It is built.** That alone
   would make me give the platform a real chance.

## Things that would close my tab (negative)

1. **Empty database on day one.** Nothing to look at = no platform.
2. **No `documentsVerifiedAt` on the listing.** The badge I came for
   is not surfaced anywhere I can see it.
3. **No `closedDealCount` on agent profiles.** The single number that
   would tell me an agent is real and busy is missing.
4. **Zero notifications from the platform during my whole journey.**
   The platform never spoke to me — and silence is what scammers
   sound like.
5. **`POST /auth/register` returns nothing.** I have no idea if my
   account exists.

---

# Top 5 things I'd fix tomorrow (ranked by impact on MY trust)

1. **Surface `documentsVerifiedAt` (the verified-property badge) on
   every listing in the index, not just the detail.** This is the
   single field that decides whether I would click any listing card.
   No badge on the card = treat-every-listing-as-fake. Without this,
   nothing else here matters.

2. **Add `closedDealCount` and `medianResponseMinutes` to
   `PublicUserProfile`.** Aggregate rating + review count is too
   fuzzy. "31 closed deals, replies in 32 minutes" is the line that
   would convince me to call an agent. The platform already has the
   data internally (notification timestamps, listing CLOSED status);
   it just needs to surface it.

3. **Send the reporter a sync notification when a report is filed,
   and surface a "this listing has been reported N times" pill on
   the listing detail.** A moderation channel that you can't see
   the result of feels like no channel at all. And the next Ngozi
   needs to see the warning *before* she requests an inspection.

4. **Add `intent: enum [RENT, BUY, RENT_TO_BUY]` to the offer
   request and response.** My whole reason for using this platform
   is rent-to-buy with Moniepoint financing. Cramming that into a
   free-text message is not a contract — it's a hope.

5. **Speak to the user.** Every action I take should generate a sync
   notification I can see in `/notifications/mine`. Verification
   submitted, inspection requested, offer submitted, report filed —
   all of it. Empty inbox after nine days is the loudest possible
   signal that the platform doesn't care whether I'm there.

---

*— Ngozi Eze, Surulere, the day after the platform finally let me
sleep without checking my bank statement.*
