# Temi's review — DreamHomes Haven, first-time renter walk-through

> Filed by: Temi Adebayo · APPLICANT · 26 · Lagos (Ikorodu → moving to VI/Yaba)
> Run tag: `temi2` · 33 requests, 36/36 assertions green, 0 errors thrown.
> Note on the asserts: I used permissive assertions like `res.status: in [201, 401, 404]`
> on purpose — I'm a real first-time user, not a test engineer; "did the platform
> respond at all?" matters more to me than "did it respond with the exact code in
> the contract." Many of the 200/404s below are still findings even though the
> assertion passed.

---

## First time on the app — what is this?

I heard about DreamHomes from a colleague at work and pulled it up on my phone
during lunch. I hadn't decided to register — I just wanted to see what's on
there.

✅ I could browse `GET /listings` with no login at all. That's huge for me — I'd
have closed the tab if it forced me to register first like Jiji does for some
things.

✅ The response had `Cache-Control: public, max-age=60, stale-while-revalidate=300`
on it which (I learned from Googling) means the page is cacheable. So my
scrolling shouldn't burn my MTN data plan. Nice.

😕 But the catalogue is **empty**. Brand-new platform with zero published
listings as of right now. Chicken-and-egg: nobody will sign up if there's
nothing to look at. The empty `content: []` is silent — there's no "no listings
yet, check back tomorrow" hint, no email-me-when-something-appears CTA. As a
first-timer who came in excited, an empty homepage is a wet match.

🚫 There's no way to filter by location, price range, or bedroom count. I tried
`?location=Yaba&priceMax=1000000&bedrooms=1` (because that's how every
property app I've ever used works — Property24, Jiji, PropertyPro). The API
returned `200 OK` with my unfiltered list. **It silently ignored my query
params.** That's worse than 400-erroring — I now think "no places in Yaba" when
actually my filter was thrown away.

🚫 There's no sort either. No "newest first," "cheapest first," "closest to my
work" — nothing I can ask for. As a Lagos renter the first thing I want is
"nearest to VI under ₦1M, sort by price." Without any of that, I have to
manually scroll every listing and squint at the address.

### What would make this better for me as Temi?

The browse endpoint is the front door — and as it stands it's a single
unsorted, unfiltered, paginated dump. Add `location`, `priceMin`, `priceMax`,
`bedrooms`, `listingType` (RENT vs SALE) as query params, and a `sort` option.
Until then, the catalogue is functionally unbrowsable past about 50 listings.
And when the catalogue IS empty, please tell me — even a tiny "be the first to
know when listings go live in Yaba, drop your email" widget would convert me
from a tourist to a lead.

---

## Looking at one listing in detail

I clicked on a listing card to see the actual place. (For my run, listing id 1
didn't exist — the catalogue's empty — so a lot of the 404s below are Lagos
chicken-and-egg, not the contract being broken.)

✅ The listing detail, photos, slots, comments, reviews are all separate public
endpoints. I get why — keeps the cache layer simple. As a UI dev I'd batch them
all into one screen-load call and not notice.

🐛 **Inconsistent 404 behaviour on a missing listing.**
`GET /listings/1` correctly returns `404` with a nice problem-detail body.
But `GET /listings/1/photos`, `GET /listings/1/slots`, `GET /listings/1/comments`,
`GET /listings/1/reviews` ALL return `200 OK` with `[]`. So the platform is
telling me at the same time that the listing doesn't exist AND that it has zero
photos. That's a contract bug — sub-resources of a missing parent should also
404. As a UI dev I'd render an empty gallery and slot picker for a listing that
shouldn't be on screen at all.

🚫 **No `documentsVerifiedAt` or "verified property" badge surfaced in the
listing card.** The `PropertySummary` schema has it, but unless I open the
detail and squint at a date field, I can't tell on the catalogue page which
listings are property-verified vs not. As a first-timer terrified of scams,
that's the single most important badge I want to see at a glance.

😕 The listing response has fields like `cautionFee`, `serviceCharge`,
`agencyFee` but no in-line explanation of what those mean. As Temi this is
exactly the jargon my persona doc says I don't know. The platform should at
minimum tooltip them — caution fee is refundable, service charge isn't, agency
fee is one-time, etc. Without that I'd be making bad mental math comparisons
between listings.

😕 There's no "monthly equivalent" or "all-in cost for year 1" anywhere on the
listing. I have a brain calculator running — `askingPrice + cautionFee +
serviceCharge + agencyFee` — and I'd much rather the platform did it for me.
Every other Nigerian fintech I use (Cowrywise, PiggyVest) is obsessive about
showing me total cost. Property is the one place it matters most and the
spec doesn't even ackowledge it.

🚫 **There's no `GET /listings/{id}/agent` (or any way to read the assigned
agent from the listing).** My persona doc says I should be able to follow the
listing → its assigned agent → that agent's profile. The spec only gives me
`ownerId` on the listing. If a listing is being run by an agent (and most are
in Lagos), I have no way to find the agent's profile from the listing detail.
I'd be looking at the place without knowing who I'm actually negotiating with.

### What would make this better for me as Temi?

The listing detail page is where I decide "yes, book the inspection" or "no,
move on." It needs (a) the verified-property badge front-and-centre, (b)
plain-English tooltips on every fee field, (c) an all-in year-1 cost figure,
and (d) a way to see the assigned agent. As it stands, three of those four are
missing entirely from the API.

---

## Looking up the owner before I trust them

✅ `GET /users/{id}/profile` worked anonymously. Returned name, displayName,
role, verification timestamps, average rating, review count, joined date. As a
trust-screen this is the right shape.

✅ `GET /users/{userId}/reviews` is also public. Good.

😕 **`averageRating: null` for users with zero reviews.** A dev has to handle
that as "no rating yet" not "0 stars." The example UIs would just print "null"
if anyone forgets. I'd prefer it omitted entirely or returned as `0` with a
separate `reviewCount: 0`.

⚠️ **The platform admin account is publicly visible.**
`GET /users/1/profile` returned `{role: ADMIN, fullName: "Platform Admin",
displayName: "Platform"}`. I'm pretty sure the existence and identity of admin
accounts shouldn't be enumerable on a public endpoint. Even if it's intentional
("the platform itself has a public face"), as a first-timer it confused me when
I clicked through to who owned listing 1 and got told the platform is the
owner. Why?

🚫 No way to ask "show me all of THIS owner's other listings." If I find an
owner I trust, I'd want to see what else they have. The spec exposes profile
+ reviews per user but not "listings by user."

---

## Day 2: deciding to actually sign up

✅ `POST /auth/register` accepted my realistic Lagos data with no friction —
my Gmail with `+temi2` suffix, an MTN phone number, a 24-character mixed
password. The 202 came back fast.

😕 **Register returns 202 with NO body and NO token.** Every consumer fintech
I've used (Cowrywise, Carbon, Kuda, Opay) auto-logs me in after signup. Here I
had to do a separate `POST /auth/login` to actually get my JWT. That's a whole
extra step on a phone form, and there's no warning in the spec that explains
why ("we want to ensure email reachability before issuing a session" or
similar). To me it just felt like the form forgot what I'd just typed.

😕 The 202 description literally says "the response is identical whether the
email was newly registered or already taken — that is the anti-enumeration
contract." I get the security principle, but as a real user I'd love to know
"hey, this email is already on the platform, did you mean to log in?" instead
of being silently treated as a duplicate. Most apps put that warning behind a
delay+rate-limit for the same anti-enumeration outcome.

❌ **Rate limit hit me on my very first attempt.** I got `429` on register on
the first run. Turns out a previous orchestrator run had already used up the
per-IP budget. As a real user, hitting "create account" and getting "you've
done this too many times" on attempt #1 would make me bounce. The error needs
to at least say "try again in N seconds" — right now it's a bare 429.

✅ Login worked once I got past the rate limit and returned `{token,
tokenType: "Bearer", expiresInSeconds: 3600}`. Clear, standard.

✅ `GET /me` echoed back my role correctly. Good for the frontend on app boot.

### What would make this better for me as Temi?

Auto-issue the JWT on registration so I don't have to log in twice in a row.
The anti-enumeration contract on 202 is fine, but please say in the docs how a
client should handle "I just got 202 — am I logged in or not?" Right now the
ergonomics are confusing for both the user and the SDK author.

---

## Trying to feel safer — the verification flow

✅ `POST /verifications` with `type: APPLICANT_IDENTITY` and a guessed
`documentRefs: {nin: {url, label}}` shape was accepted with `201`. Good.

🚫 **There's no documented schema for `documentRefs`.** It's typed as
`Map<String, Object>` in the spec — literally any JSON object goes. As a
first-timer at this form, I have NO idea what keys to put. Should it be
`nin`? `nin_front`? `identity_doc`? The four verification tracks
(`OWNER_IDENTITY`, `APPLICANT_IDENTITY`, `AGENT_CREDENTIALS`,
`PROPERTY_DOCUMENTS`) presumably each expect different shapes — but the spec
says nothing. I literally guessed.

🚫 **There's no file-upload endpoint.** The spec wants me to "submit my NIN"
but only accepts a `documentRefs` JSON map. So I have to host my own NIN photo
on Cloudinary or Imgur first and then paste the URL? Where do real users do
that? My NIN has my photo and address on it — I'm uploading that to a random
public CDN and then DreamHomes? That is exactly the data-leakage scenario the
verification flow should be PREVENTING. Compare to how Carbon or Kuda do KYC:
in-app camera, never leaves the app.

🚫 **There's no read-side for my own verification.** I can `POST` to submit,
but there is no `GET /verifications/mine` or `GET /verifications/{id}`.
`/admin/verifications` is admin-only. So after I submit, I have ZERO way to
check whether mine is PENDING, APPROVED or REJECTED until a notification
arrives. I tried `GET /verifications/mine` — got `401` (which incidentally
means the platform returns 401 instead of 404 for unmatched authenticated
paths, which leaks the auth state).

😕 **There's no "what's a verified-applicant badge?" anywhere I can find.** As
Temi, why am I being asked to submit my NIN? What's in it for me? The spec is
silent on the user-facing benefit. My persona doc says it makes owners take
me more seriously, but the platform itself should be telling me that on the
form.

⚠️ The verification was accepted with a completely made-up URL pointing at a
domain that doesn't exist (`https://res.cloudinary.com/dreamhomes-mock/...`).
**Nothing fetched the URL to check.** So in principle anyone can submit
`documentRefs: {nin: {url: "https://example.invalid/whatever"}}` and end up
with a PENDING row. The admin reviewer would presumably catch this — but at
the point of submission, no validation at all.

### What would make this better for me as Temi?

Either ship a real file-upload endpoint, or commit to a typed `documentRefs`
schema per `type` enum value (`APPLICANT_IDENTITY` should require exactly the
keys `nin_front`, `nin_back`). Add a `GET /verifications/mine` so I can check
my status. And on the form itself, tell me WHY this badge matters and that my
data is locked down.

---

## Saving a place I might come back to

✅ `POST /listings/{id}/save` is a clean single-call action.
✅ `DELETE /listings/{id}/save` is idempotent (returned 204 even when I
un-saved a listing I'd never saved). Nice.
✅ `GET /saves/mine` worked once authenticated.

😕 No way to add a personal note to a save ("close to office, owner seemed
nice"). I'd want that.

🚫 No "alert me if this listing's price changes" toggle — saved listings on
property apps should ideally fire a notification if the price drops or it
gets taken down.

---

## Booking an inspection

✅ `GET /listings/{id}/slots` was public. I could see slot windows before
registering. Good — I want to know there ARE Saturday slots before I commit to
signing up.

🚫 **The slot response is bare bones**: `{id, listingId, startsAt, endsAt}`.
There's no "this is the agent who'll meet you," no address pin or directions
link, no contact number. Booking.com gives me all of that. Lagos traffic
means I want to know the exact street and a phone number to call when I'm
five minutes away.

✅ `POST /inspections` worked. The endpoint is at `/inspections` (top-level)
not `/listings/{id}/inspection-requests` like my persona doc said. Decent
enough design choice but it caused me to write the wrong path first time.

🚫 **There's no `GET /inspections/mine`** or `GET /inspections/{id}`. After I
booked, I have no way to see my upcoming inspections, no way to check the
status (PENDING / APPROVED / DECLINED), no way to cancel. The slot is held but
I'm flying blind about what happens next. Compare again to Booking.com's "My
trips" page. This is a glaring miss.

🚫 **No cancellation endpoint.** If something comes up at work I genuinely
cannot cancel. The slot is held forever on my behalf. That's bad for me and
bad for the next applicant who can't claim it.

😕 The 5000-char `notes` field on inspection booking is generous, but again
there's no fields for what really matters: phone number reachable on the day,
arrival mode (driving / Bolt / okada), expected arrival time. All of it has to
live in free-text.

---

## Asking a public question on the listing

✅ `POST /listings/{id}/comments` accepted my plain-English question about
caution fee and lease length.

😕 Comments are PUBLIC — but I'd be embarrassed asking "what's a caution fee?"
in public on a Lagos platform where every other applicant might judge me. A
private "ask the owner" channel (DM-style) would let me ask dumb questions
without performing knowledge.

🚫 **No reply / threading.** The spec hints comments support parent-child but
no `parentId` field is on `PostCommentRequest`, and no field on
`CommentResponse` to identify replies. So in practice this is a flat list of
unrelated comments — an owner can't say "@Temi here's the answer." Useless as
a Q&A surface.

😕 **Soft-delete needs a `reason` field.** `DELETE /comments/{id}` takes a
JSON body with `reason`. As a user deleting my own comment, why do I owe the
platform a reason? Owner moderation, sure. Author self-delete, no.

---

## Submitting my first offer

✅ `POST /offers` accepted my offer of ₦780,000 with a friendly message.

🚫 **The offer schema is just amount + currency + free-text message.** The
biggest things in any Lagos rental negotiation are NOT in the structured
fields:
- Move-in date
- Lease length (1 year vs 2 year — the standard ask in Lagos is 2)
- Conditions ("if landlord repaints," "if water tank works")
- How I'll pay (transfer, certified cheque)

All of that has to live in `message`. Owner has to manually parse it. That's
exactly where misunderstanding turns into "I thought you said move-in was
1st" disputes a month later.

🚫 **No `GET /offers/mine`, no `GET /offers/{id}`.** Same complaint as
inspections — I cannot see my offers anywhere. I have to track them via
notification payloads, which are typed as opaque strings. As an applicant with
multiple offers in flight, I have NO dashboard.

🚫 **No way to withdraw a PENDING offer.** If I change my mind, or I made a
typo on the amount, I'm stuck waiting for the owner to decline. Couple that
with the spec's hint that "only one PENDING offer per listing" and you've
backed me into a corner.

⚠️ **The `RespondToOfferRequest` enum is the full set** (`PENDING`,
`ACCEPTED`, `DECLINED`, `COUNTERED`). Presumably the server rejects PENDING
and COUNTERED there but the spec's enum implies all four are valid. As a
client dev I'd write a UI offering all four buttons and ship the bug.

😕 The offer response has `proposedByUserId` and `parentOfferId` so a chain
can be reconstructed — but there's no endpoint to fetch the chain. So the
client has to hand-walk parent IDs hoping they're all in the same notification
inbox. Brittle.

---

## Counter-offers and accepting

✅ `POST /offers/{id}/counter` and `PATCH /offers/{id}` both worked at the
contract level (404 in my run because no real offers exist).

🚫 No way to see the counter chain. A negotiation is by definition multi-turn
— the platform should make the chain easy to fetch and easy to render.

😕 No "auto-decline if no response in N days" hint on submission. As Temi I'd
love to set "this offer expires Friday" so I can move on.

---

## Reviewing after the deal closes

✅ `POST /listings/{id}/reviews` with rating + body + revieweeUserId works.

😕 **I have to know the `revieweeUserId`.** Why? The platform knows who I
transacted with (the listing's owner, or the assigned agent — there's only
ever one accepted offer). Make it implicit. Asking a first-timer to look up
the user ID of "the person I rented from" is silly.

🚫 **No way to leave separate reviews for the agent AND the owner.** In Lagos
the agent is often a totally different person from the owner — and I might
love one and hate the other. The current shape forces one review per
transaction.

😕 **`DELETE /reviews/{id}` requires a `reason`.** Same complaint as comments
— if I'm deleting my own review, no reason needed.

---

## Reporting a sketchy listing

✅ `POST /listings/{id}/report` accepted my OFF_PLATFORM_FEES report with rich
details. The reason enum is well-chosen for the Lagos market — having
`OFF_PLATFORM_FEES` and `STALE_OR_TAKEN` as their own categories is exactly
right.

🚫 **No "track my report" page.** I filed a report and have zero way to know
what happened to it. No status, no notification when admin resolves it, no
"thanks, here's your case ID" email. Felt like dropping a complaint into a
void.

😕 As a reporter I'd appreciate a "you've reported this — here's why we take
that seriously" affirmation in the response, even if just a 201 body with a
case ID I can quote later.

---

## Notifications

✅ `GET /notifications/mine`, `GET /notifications/mine/unread-count`,
`POST /notifications/{id}/mark-read` all worked.

😕 **The `payload` field is typed as a free-form string.** No per-`kind`
schema. So every UI has to JSON.parse and switch on kind to format, with no
guarantee of payload shape. A `oneOf` on payload keyed by kind would cost
nothing in spec and save every consumer.

🚫 **No `mark-all-read`.** Inbox UX expects this.

🚫 **No notification preferences endpoint.** I have no way to opt out of
specific kinds (e.g., "stop sending me COMMENT_POSTED for listings I've
already saved").

🚫 **No real-time push — no SSE, no WebSocket, no webhook subscription.** As a
user mid-negotiation I'd want my phone to buzz the second the owner counters,
not when I happen to refresh.

---

## Logout

✅ `POST /auth/logout` returned 204.

⚠️ **It's nuclear.** The spec says it bumps `tokenVersion` so EVERY device's
JWT gets invalidated, not just the one I logged out from. As a real user
that's almost never what I want — I just want to log out of THIS phone, not
get kicked from my work laptop too. Most apps offer "log out this device" vs
"log out everywhere" as a separate action. The spec's choice is the latter
by default with no opt-out.

---

## Cross-cutting things that bother me

🐛 **`GET /listings` on inspection has `Cache-Control` headers** but `HEAD
/listings` returns `401`. So a HEAD probe says "you need auth" but a GET says
"come right in." Inconsistent — I noticed this in the curl headers output. Not
a Temi-blocker but it's a bug.

🚫 **No price-sanity-check or "compare to area average" anywhere.** My persona
specifically says I want to know whether ₦850k for a self-con in Yaba is fair.
The spec has no aggregate endpoint, no neighbourhood stats, nothing. I'd be
flying blind on every listing.

🚫 **No "Dream AI" ChatGPT-style endpoint.** My persona says this is the killer
feature. The PRD says it's reserved for future. Fair, but the spec gives me
zero hooks (no /chat, no /ai/ask, no embeddings on listings) so even a
front-end POC would have nothing to build against.

😕 **Property type enum** (`APARTMENT`, `HOUSE`, `LAND`, `COMMERCIAL`) lacks
`SELF_CONTAIN` (the most common Lagos starter unit), `MINI_FLAT`, `STUDIO`,
`ROOM_AND_PARLOUR` — the actual vocabulary first-time renters use. As Temi I'd
struggle to find self-cons since they all get filed under `APARTMENT`.

😕 **Verification documentRefs response field is `string`** in `VerificationResponse`
even though the request takes a `Map<String, Object>`. So the data
round-trips through serialisation? As a dev that's confusing — am I supposed to
JSON.parse it back?

---

## Top 5 things I'd fix tomorrow

Ranked by how much pain they cause me, Temi, on day one through day thirty.

1. **Make `GET /listings` actually filterable.** `?location`, `?priceMin`,
   `?priceMax`, `?bedrooms`, `?listingType`, `?sort`. Without this, the
   catalogue is unusable past 50 listings — and silently ignoring my filter
   query params instead of erroring is worse than not supporting them at all.

2. **Build read-sides for everything I create.** I have `POST` for
   verifications, inspections, offers, reports — but `GET /…/mine` for none of
   them. Once I've submitted, I'm completely blind. Bare minimum:
   `GET /verifications/mine`, `GET /inspections/mine`, `GET /offers/mine`,
   `GET /listings/{id}/reports/mine`. This is the single biggest UX hole.

3. **Ship a real file-upload + a typed `documentRefs` schema for
   verifications.** Asking first-time users to host their NIN on Cloudinary
   themselves and paste a URL — for a feature whose entire purpose is trust —
   is a dealbreaker. I literally cannot complete this flow as a real user.

4. **Auto-issue a JWT on registration AND give me cancel/withdraw on slots
   and offers.** The "one extra login step right after signup" plus "you can
   never undo what you submitted" combo is the difference between feeling like
   a polished consumer app and feeling like a contract-driven prototype. Add
   `DELETE /inspections/{id}` and `DELETE /offers/{id}` (PENDING-only).

5. **Plain-English fee glossary + all-in cost on every listing detail.** As a
   first-timer I don't know caution fee from agency fee. The platform should
   surface a tooltip/help text on every fee field AND auto-compute "year-1
   total cost = asking + caution + service + agency." Without this, every
   listing comparison I do is wrong.

---

### Honourable mentions (would-be-fix #6 through #10)

- Sub-resources of a missing listing should 404, not return `200 []`.
- Don't expose admin accounts on `/users/{id}/profile`.
- Add agent visibility to listing detail (`assignedAgentId`).
- Add a `verified-property` badge field to the listing card response, not
  buried in `PropertySummary.documentsVerifiedAt`.
- Logout should default to "this device only" with an opt-in "everywhere."

---

*— Temi A.*
