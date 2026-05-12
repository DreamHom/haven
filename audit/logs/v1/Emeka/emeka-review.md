# Emeka's review — DreamHomes Haven, week one

> *"You build a platform for agents but you forgot that agents sell themselves before they sell anything."*

I'm Emeka. 29. Port Harcourt. Solo agent — no office, just my bike,
my Glo line and my reputation. I tried Haven this week. Here's what it
felt like.

---

## Run summary

- **Collection**: `audit/bruno/Emeka/` — 5 day-folders, 22 requests
- **Best run**: `run_tag=emeka1`, 22/22 requests reached the server, 24/27 assertions passed
- **3 "failed" assertions** are all blocked-by-cross-persona requests (no
  Biodun has invited me, so my placeholder variables are empty, the URL
  becomes `/agent-listings//accept` and the security filter rejects it
  before it can route — 401). These are recorded as BLOCKED, not bugs.
- **Auth notes**: hit the per-IP rate limit twice during re-runs. The
  spec says register/login throttle to 429 — this is correct.
- **Anomaly worth looking into**: `POST /api/auth/register` once returned
  **401** for a perfectly-formed AGENT payload from raw curl, after the
  rate limit ostensibly cleared. The spec contract is *"always 202,
  regardless of whether the email was already registered"*. I couldn't
  reproduce reliably (it cycled back to 429), but if the limiter or any
  other filter ever sneaks a 401 onto this path, the anti-enumeration
  contract is broken — a probe can tell when the limiter is "open" vs
  not by status alone.

---

## First impressions — does this thing look like it was built for an agent?

### What I tried

1. `GET /api/listings` — anonymous browse. Wanted to see if PH listings exist.
2. `POST /api/auth/register` as AGENT with my license number.
3. `POST /api/auth/login`, `GET /api/me`.
4. `GET /api/users/{id}/profile` — my own card, the way owners will see me.

### Per-request notes

> ✅ Browse listings worked, returned a paginated body. Cache headers stamped — feels production-grade.
> ⚠️ Register returned 202 with NO body. I have no acknowledgement. Did it accept me? Did the email already exist? The "anti-enumeration" reasoning is for security but for me, the new user, it feels like throwing my CV into a black box.
> ✅ Login returned a JWT. Token in hand. Reasonable 1h expiry.
> ✅ /me returned my role=AGENT and my user ID. One round-trip to confirm I exist.
> ⚠️ My profile came back with: name, role, identityVerifiedAt, agentCredentialVerifiedAt, suspended, averageRating, reviewCount, joinedAt. Nothing else.

### What would make this better for me as Emeka?

The first place an owner is going to land — `GET /users/{id}/profile`
— is where I sell myself, and right now there is nothing to sell with.
No bio. No phone, even masked. No photo URL. No service area (Port
Harcourt). No language(s). No "active listings" count. No
**closed-deal count**. No **response rate**. No **average response
time**. My persona doc literally lists those last three as the reason
I'd pick this platform. Story 9 calls them "future." For me they're
not future — they ARE the product. Without them I'm a row of nullable
timestamps.

🚫 No "city" or "service area" filter on `GET /listings` either, so
even if an owner was trying to find an agent like me, they couldn't
narrow to PH.

🚫 No `displayName` or photo on the profile schema even though the
register payload accepts a `displayName`. Where does it surface?

---

## Getting my badge — uploading my credentials

### What I tried

1. `POST /api/verifications` with type=AGENT_CREDENTIALS, payload pointing at a hosted PDF of my NIPRA license.
2. Tried to submit AGAIN to "refresh" my scan.
3. Polled `/notifications/mine` to see if anything happened.

### Per-request notes

> ✅ First submission returned 201 PENDING with my verification id. Clean.
> ✅ Second submission correctly rejected with 409. The duplicate guard works.
> ⚠️ Inbox empty. No way to see "your AGENT_CREDENTIALS submission is in queue, position N." I just have to refresh and pray.

### What would make this better for me as Emeka?

The real frustration here is **`documentRefs` is a free-form JSON
object with zero validation in the contract**. I had to invent a
shape — `{"kind":"NIPRA_LICENSE","ref":"https://..."}` — based on the
single example in the spec. I have no idea if the admin's queue
expects `ref`, `url`, `link`, `s3Key`. I'm hosting my own PDF on a
public URL because **there is no file-upload endpoint for
verifications**. For a NIN slip or a license that's a privacy
nightmare — anyone with the link sees my full document.

🚫 No `POST /verifications/{id}/files` (multipart). Forces me into "host the PDF on Google Drive with a public link."
🚫 No `GET /verifications/mine` to check status from my side.
🚫 No `PATCH /verifications/{id}` or `DELETE /verifications/{id}` to amend a pending submission. If I uploaded a blurry scan, I'm stuck waiting for the admin to reject before I can resubmit.
😕 The duplicate 409 is correct but unhelpful — the error body doesn't tell me when my pending one was submitted, so I don't even know if it's been sitting for an hour or three days.

---

## Waiting around — what do I do while I wait for assignments?

### What I tried

1. `GET /api/agent-listings/mine` — my pipeline.
2. `GET /api/listings` again, scanning for owners I could pitch.
3. `GET /api/listings/{id}/comments` to see if I could find owners through threads.
4. `GET /api/notifications/mine?unreadOnly=true` — refreshing my inbox.

### Per-request notes

> ✅ My pipeline call returned 200 with empty content. Pagination wrapper present. Clean.
> ⚠️ No way to filter `agent-listings/mine?status=REQUESTED` — I get one mixed list of REQUESTED + ACCEPTED + DECLINED + REVOKED. Every CRM separates "open invites" from "active deals." Spec doesn't.
> ⚠️ Browse listings has only `pageable` — no `?city=PH`, no `?hasAgent=false`, no `?status=`, no `?priceMin/Max`, no `?type=apartment`. I cannot prospect.
> ✅ Comment list on listing 1 returned 200 (empty thread).
> ✅ Inbox poll worked. Empty.

### What would make this better for me as Emeka?

This is dead time. The platform gives me **literally nothing to do**
while I wait. I'm not a passive listing — I'm a salesperson with a
bike and 2-3 free hours a day. I want:

🚫 A **"listings looking for an agent" feed** — owners who flagged "I want help" or who don't yet have an assigned agent. This is the single highest-leverage feature you could ship for the agent persona.
🚫 A way to **DM an owner** through the platform. Right now there's `POST /listings/{id}/comments` but that's a public thread under the listing — embarrassing for me to pitch in front of every applicant.
🚫 A **service-area declaration** on my profile so when an owner is filtering by city, I show up.
😕 The pipeline endpoint is paginated by default at size=20 with no status filter. Once I'm rolling 7 active + a backlog of declined invites, I'll be paging through dead rows to find the live one.

---

## Receiving my first assignment

### What I tried

1. `GET /api/notifications/mine` — looking for AGENT_ASSIGNMENT_REQUESTED.
2. `GET /api/agent-listings/mine` — looking for a REQUESTED row.
3. `POST /api/agent-listings/{id}/accept` — would have accepted if I had one.

### Per-request notes

> ✅ Inbox query worked but returned nothing — BLOCKED on Biodun (OWNER persona) calling `POST /listings/{id}/agent-assignment` with my agentId.
> ✅ Pipeline query worked, no REQUESTED rows yet — same BLOCKED reason.
> ⚠️ `POST /agent-listings//accept` (placeholder unset) returned 401. Reasonable defence-in-depth, but it means I can't tell from the status code whether "the assignment doesn't exist" or "I'm not authenticated" — both are 401.

### What would make this better for me as Emeka?

The contract LOOKS right here — REQUESTED → ACCEPTED with notification
fan-out is exactly Story 4. But the holes I'd flag the moment a real
invite arrived:

🚫 No way for me to attach a short note when I accept ("Thanks Mr Biodun, I'll be at the property Saturday."). Owner just gets a system event. For a relationship business that's cold.
🚫 No `agent-listings/{id}` GET — I can't deep-link into a single invite to read the owner's reason in detail. I have to scroll my whole pipeline page.
🚫 No way to see the owner's profile from the invite — I want to vet WHO is hiring me before I accept. The schema has `requestedByOwnerId` but no embedded summary.
😕 The `decisionReason` field on AgentListingResponse is only populated on decline — if accept could carry a note, it'd live there too.

---

## Running an inspection on the platform

### What I tried

1. `POST /api/listings/{id}/slots` — open a Saturday morning window.
2. `GET /api/listings/{id}/slots` — confirm it's public.
3. `GET /api/notifications/mine?unreadOnly=true` — wait for INSPECTION_REQUESTED.

### Per-request notes

> ⚠️ All three were blocked by missing `assigned_listing_id`. Got 401s as the URLs collapsed to `/listings//slots`.
> ⚠️ Reading the spec text on `POST /listings/{listingId}/slots`, the description literally reads *"Authorisation: the listing's owner (today). Assigned agent..."* — and trails off. **The contract is unfinished mid-sentence**. As an agent who needs to know whether I have authority on this endpoint, that's the worst possible state.

### What would make this better for me as Emeka?

🐛 Spec doc is broken on `POST /listings/{id}/slots` — "Assigned agent..." sentence is cut. Either I can open slots or I can't. Per Story 6 I should be able to. The contract has to say so explicitly so the frontend builds the button.
🚫 No `DELETE /listings/{id}/slots/{slotId}` or PATCH to cancel/reschedule a slot. If something comes up Saturday and I need to push by 2h, I can't.
🚫 Slot endpoint accepts `startsAt`/`endsAt` only — no capacity (one slot = one inspection?), no notes for the applicant ("ring bell #3"), no recurring slots. I'd have to POST a separate row for every Saturday this month.
🚫 No way for me to BLOCK times where I'm unavailable across all my listings (I'm at a wedding Saturday). I'd have to manually omit each listing's slot.
😕 The inspection-request notification flow says it fans out to me + owner. Good. But if owner and I both reply, who wins? The spec doesn't say. I'd want to see "owner already responded" in my inbox row.

---

## My profile after the deal closes

### What I tried

1. `GET /api/users/{id}/profile` — re-check my card.
2. `GET /api/users/{id}/reviews` — read my reviews.

### Per-request notes

> ✅ Profile call works, schema is what it is.
> ✅ Reviews call works, paginated.

### What would make this better for me as Emeka?

🚫 **No closed-deal count**. The single number that matters most for an agent's pitch — "I closed 23 deals" — is not in the schema. The persona doc says it's "derivable" from review counts where the reviewer is an applicant on a CLOSED listing. Derivable in a query is not the same as displayed on my card.
🚫 **No response-rate / response-time stats**. Persona doc Story 9 — explicitly future. For me this is "the day I close my account if you don't ship it" because that's how owners decide.
🚫 **No way to respond to a review**. If a tenant gives me 3 stars over a misunderstanding, I have zero right of reply. Other platforms (Airbnb, Bolt, Uber) all let the reviewee comment.
🚫 **No `DELETE /reviews/{id}` for me as the reviewee** — only the original reviewer can soft-delete. If someone reviews me out of spite I can't even flag it.
😕 Review schema doesn't include the listing id or address in line. I'd have to click into each one to remember WHICH deal they're talking about.

---

## Cross-cutting things that bothered me

🐛 **Anti-enumeration contract is fragile.** The register endpoint promises "always 202" — but rate-limit window edges, suspended-user paths, and possibly other filters can return 401 / 429 / other. A probe just has to spam the endpoint and watch for status drift to learn whether an email exists or whether the limiter is open.
🐛 **Spec cuts off mid-sentence** on `POST /listings/{id}/slots` — a build tool would propagate that ambiguity straight into the frontend.
😕 **`documentRefs` is `additionalProperties: { type: object }` with zero shape contract.** Verification submitters guess. Verification reviewers guess. We will end up with five different doc-shape conventions in production.
😕 **Pagination is consistent (good!) but never documents which sort key applies.** "newest first" is mentioned in some descriptions, not others. I can't ask "show my oldest pending invite first."
⚠️ **No `Last-Modified` / `ETag` on `/notifications/mine`.** I'm polling this from a phone on a sketchy 3G connection. Every poll re-downloads the full page. Stamp the headers.

---

## Top 5 things I'd fix tomorrow

Ranked by how much each one would change my week as Emeka:

1. **Ship `closedDealCount` + `responseRate` + `avgResponseTimeMinutes` on `GET /users/{id}/profile` for AGENT role.** This is THE pitch. Without it I'm a name and a star. Persona doc Story 9 — should be Story 0.
2. **Add `?status=REQUESTED|ACCEPTED|DECLINED|REVOKED` (and ideally `?listingId=`) to `GET /agent-listings/mine`.** Mixed-state pagination is unusable past the first 5 invites.
3. **Add a "find an agent" / "find a listing without an agent" surface.** Either: (a) `GET /listings?hasAgent=false&city=PH` for me to prospect outwards, OR (b) an "agents directory" `GET /agents?city=PH&verifiedOnly=true` for owners to find me. Right now neither exists and the marketplace can't form.
4. **Add `POST /verifications/{id}/files` (multipart) and `GET /verifications/mine`.** Stop forcing me to host my license PDF publicly. Stop leaving me in the dark after I submit.
5. **Finish the auth-statement on `POST /listings/{id}/slots` AND let the assigned agent run slots end-to-end (story 6 contract).** Without this, owners delegate to me on paper but nothing actually moves to my plate.

That's it. Good bones — verification flow, agent-listing handshake, notification fan-out, public profile shape are all the right primitives. But the agent persona is half-built. The product knows I should be able to manage 7 listings at once but doesn't yet give me the pipeline view, the prospecting feed, or the trust signals to make the case.

I'll be back next week to see what's shipped.

— Emeka
