# Amaka's review — one week with DreamHomes

> *I'm Amaka. 41. Civil servant in Abuja. I inherited two flats in Lekki from
> my late father and I've been getting cheated by "agents" for two years. A
> friend told me about DreamHomes. I ran through the whole thing on my phone
> over five evenings. Here's what I found, in the order I lived it.*

**Final run**: 35 requests, 11 passed, 24 failed, 24/65 assertions green
(`audit/reports/amaka.html`). I ran the collection five times in total
(`amaka2` through `amaka7`) — the failures repeated, and the reasons below
are why.

---

## Day 1 — what is this thing?

I sat on my bed with a cup of Bournvita and tried to register. Quick story
in five clicks: register → log in → check who I am → look around → check
my inbox.

> ✅ Register went through. Got 202 back exactly as the spec promised.
> ⚠️ But no token came back with register. I had to type my password AGAIN to log in. After a long day at the office that's annoying — every other app I've used in the last year gives me a session right away.
> ✅ Login worked. I got a JWT back.
> ⚠️ The login response is *just* `{ "token": "..." }`. Nothing else. I don't know when this expires, I don't know what my user ID is, I don't know if the server even noticed I asked for the OWNER role. Just a wall of text.
> ⚠️ Had to call `GET /me` separately to find out my own user ID and role. Why isn't that in the login response?
> 😕 `/me` returns `userId`, `email`, `role`, `tokenVersion`. What is `tokenVersion`? I'm a landlord, not a security engineer. It means nothing to me and it's right there in my face.
> ✅ Browsing public listings works without logging in. Good.
> 🚫 But I can't filter by Lekki, by bedroom count, or by price band. It's a firehose. As an owner doing market research before pricing my flat, that's frustrating — and as the eventual applicant on someone *else's* listing, that's worse.
> ⚠️ My notifications inbox is empty after a brand-new register. No "Welcome", no "here's what to do next". An empty page made me wonder if registration actually worked.

### What would make this better for me as Amaka?

Give me a token straight from register so I don't have to log in twice in
the same minute. Put my user object on the login response so I don't need
a separate `/me` call. And on a fresh account, drop a "welcome — your next
step is to submit your owner verification" notification in my inbox so I'm
not staring at nothing.

---

## Day 2 — trying to prove I'm a real person

The whole reason I'm here is to get that verified-owner badge so applicants
don't think I'm another Lagos scammer. This is the most important part of
the whole flow for me.

> ⚠️ I had to log in AGAIN because there's nothing telling me how long my last token lasts.
> ❌ `POST /verifications` — I'm supposed to upload my C of O and my NIN slip. But there's no file upload endpoint. The schema wants `documentRefs` as a vague object with "URLs". So my C of O — the legal title deed to my Lagos property — has to live on some random image host first, then I paste the URL into DreamHomes. That's a non-starter. I am NOT putting my Certificate of Occupancy on imgur or Cloudinary so a stranger can paste the URL anywhere.
> 🚫 No example payload anywhere in the spec for `documentRefs`. I had to guess what shape to send. I sent `{ certificateOfOccupancy: { url, label }, nin: { url, number } }` and the server accepted it — but I have NO idea if the admin will see what I meant. I might have just submitted gibberish that an admin will reject.
> ✅ It returned 201 with status PENDING. So at least it accepted my submission.
> 🚫 **There is no `GET /verifications/mine`.** This is the single worst thing I encountered all week. After hitting submit, there is NO WAY for me to check the status of my own submission from my side. I have to trust the 201 receipt and wait. As a panicky 41-year-old who just sent her property documents into a black hole, that is unforgivable.
> 😕 Because I couldn't see my submission, two minutes later I panicked and clicked submit AGAIN. The platform correctly returned 409 ("a pending verification of this type already exists") — but the only reason I did that is because the platform gave me no way to see my own pending one. This isn't a defensive feature. It's a sign of a bigger gap.
> ⚠️ I checked my public profile (`/users/{id}/profile`) — and there's no field that says "verification pending". It looks identical to a profile that never submitted. Bad for me (applicants can't tell I tried) and bad for trust both ways.
> 🛑 BLOCKED — to actually get my badge stamped, an admin has to approve my verification. Cross-persona dependency. I noted it and moved on.

### What would make this better for me as Amaka?

Two things, in order:

1. **Let me see my own submission status.** Either `GET /verifications/mine`
   or a `verificationStatus: "pending" | "verified" | "rejected"` field on
   `/me`. I cannot stress enough how much trust I lose every minute I'm
   sitting there wondering if my C of O got received.
2. **Actually let me upload the document file.** A multipart endpoint that
   stores the doc securely server-side, encrypted, only visible to admins.
   Asking me to put a Certificate of Occupancy on a public image URL is
   not safe and not realistic.

---

## Day 3 — putting my first flat on the market

This is where I expected to feel powerful and where I actually felt blocked.

> ❌ Day 3 starts with a `LoginAgain`. I got slammed with a 429 — rate limit. The platform is so aggressively rate-limited on `/auth/login` that running through my own flow in one sitting is impossible. I had to wait 60+ seconds between every day's first login. As a real user this means: if I open the app, close it, then open it again ten minutes later, I might be locked out. That's not OK.
> ⚠️ When I FINALLY got through (I had to wait and retry), the property creation itself worked. ID 27 came back, ownerId stamped as me automatically — good, it didn't trust whatever I put in the body.
> ⚠️ The property request doesn't have fields for the things Lagos landlords actually advertise: serviced/non-serviced, generator-included, year built, what floor, gated/non-gated, parking spaces. I crammed it all into "description" and hope the search can find it. Schema has nothing structured for any of this.
> 🚫 No "next-step" hint in the response. I had to know on my own that the next call is `POST /listings`. No `_links.publishListing` or even a doc pointer.
> 🐛 **Listing creation has a real bug.** Posting `agencyFee: 0` returns a 401 with no body. Posting `agencyFee: 1000` works. Posting `agencyFee: 225000` works. ZERO IS THE WHOLE POINT for me — I'm a solo owner, I'm not paying any agent. The bug effectively says "you can only list this flat if you're handing money to an agent." I confirmed this manually three times. 401 is also the wrong status code — it should be 400 or 422. Returning 401 with no body made me think my JWT was bad and wasted twenty minutes of my life.
> 🐛 **The status enum is inconsistent with the docs.** My persona doc says listings start in `OPEN`. The actual response status is `LIVE`. The enum in `ListingResponse` is `[LIVE, PAUSED, CLOSED]`. Whoever wrote the prose docs and whoever wrote the code don't agree, and I'm the one stuck in the middle.
> 🚫 No `title` or `description` on the listing itself. Only on the property. So I can't write a marketing headline like "Move-in ready! Just-painted!" — there's nowhere to put it.
> 🚫 No `GET /listings/mine` and no `GET /properties/mine` and no `GET /properties/{id}` in the spec. I just published a listing. **I cannot fetch a list of my own listings.** My only options are: remember the ID I just got, OR scroll the public firehose page until I see it. This is a property-management tool that doesn't have a "my properties" page. I don't know how this shipped.
> ✅ Anonymous discovery of the listing works — I checked `GET /listings/{id}` without a token and got the listing back.
> ⚠️ But the public listing response doesn't include my name or my verified badge. As an applicant viewing this card, you'd have to make a SECOND call to `/users/{ownerId}/profile` just to see who's renting it out. The whole point of the badge is that it's visible at first glance.
> ⚠️ Photo upload — one file per request. To put up six pictures I have to hit the endpoint six times. On my Glo connection, that's painful. My sister Funke said "is this still loading?" three times.
> ⚠️ No file size or dimension hints in the spec. I'm flying blind on what's allowed.

### What would make this better for me as Amaka?

Add a `GET /listings/mine` endpoint. Add a `GET /properties/mine` endpoint.
Fix the `agencyFee: 0` bug — it punishes the exact user the platform claims
to be for (solo owners). Pick one name for the "live" status and use it
everywhere. Let me upload several photos in one request. And put the
owner's name + verified badge directly on the listing response so
applicants see the trust signal without a second call.

---

## Day 4 — opening for inspections

Saturday morning slot, Sunday afternoon slot, then trying to create a
second listing for my other flat.

> ❌ Day 4's `LoginAgain` hit 429 AGAIN. Same rate-limit problem as Day 3. This kills any chance of running the flow end-to-end in one session.
> ⚠️ When I got through, opening a slot works. But I cannot tag the slot with who's going to be there ("my caretaker will let you in"), or prep notes ("use the side gate"). It's just a window. Anything else I have to message every single applicant individually.
> ✅ Overlap protection works — second slot at the same time got a 409. Good.
> 😕 The 409 body is RFC 7807 ProblemDetail. The title says "Conflict" — which to a normal user means nothing. The "detail" field is supposedly "slot overlaps an existing active slot". As long as the UI surfaces that and not the word "Conflict", fine.
> ✅ Public slots endpoint shows both my slots without auth. So applicants can browse my availability.
> 🚫 No "my slot dashboard" view that says "2 open slots, 0 booked". I have to count them myself.
> 🚫 Couldn't even get to "create second listing" cleanly — the cascading 401s after the failed login meant everything in Day 4 errored.

### What would make this better for me as Amaka?

Ease up on the auth rate limit, or make logout/expiry visible so I don't
have to relogin every five minutes. Let me attach a note to a slot so I
don't have to repeat the same gate instructions over WhatsApp twelve times.
And give me a "my inspection dashboard" — slots open, slots booked, who
booked them.

---

## Day 5 — receiving and responding to offers

Five days in. I'm tired. I want to wrap up — pause one listing, change a
price, close one, log out.

> ❌ `LoginAgain` — 429 again. Same problem.
> ⚠️ Pausing a listing via PATCH worked when I tested it earlier outside the run. Status changed to PAUSED. But the PATCH body only accepts `askingPrice` and `status`. I cannot fix a typo in the property address from here — and there's no `PATCH /properties/{id}` either. So if I made any mistake creating the property, the property is **immutable forever**. That's wild for a property-management tool.
> ✅ Changing price and going back to LIVE works.
> ✅ Closing a listing works — and crucially, it allowed me to close even though I had no ACCEPTED offer recorded. Good, because plenty of Lagos deals happen off-platform.
> ✅ Trying to reopen a closed listing returned 409 as expected.
> 🚫 But I would have loved a "duplicate this listing" shortcut. When I close it because the tenant moved out, I want to relist it next year without starting from zero. The spec doesn't offer this.
> 🛑 BLOCKED on offers — couldn't actually test accept/decline/counter because no applicant submitted an offer against my listing. Cross-persona dependency on Temi or Ngozi.
> 🚫 `GET /saves/mine` is actually MY bookmarks. What I'd want is the inverse — how many people have saved MY listings? That's a lead-quality signal. Not exposed.
> ✅ `GET /agent-listings/mine` returned empty. I'm not using agents and never will. Good that it doesn't force me to.
> ⚠️ Logout — the spec says it bumps `tokenVersion` and invalidates ALL my sessions. That's NOT what I want if I'm logging out of one shared laptop. A "log out this device" vs "log out everywhere" split would be much better.

### What would make this better for me as Amaka?

Let me edit the property after creating it. Give me a "duplicate listing"
button. Show me how many people saved my listing — that's gold for pricing.
And split logout into "this session" vs "everywhere".

---

## The biggest thing I want to say

The platform feels like it was built for the *audit trail* and the *admin
console* first, and for me — the actual owner who's paying for nothing and
needs a feeling of control — second. There's no "my listings" page. There's
no "my properties" page. There's no way to see my own pending verification.
The `tokenVersion` is right there in my face. Logout nukes everything.

I have control over a lot of things I'd never click. I have zero control
over the things I'd click every single day.

---

## Top 5 things I'd fix tomorrow

1. **Let me see my own verification status.** Add `GET /verifications/mine`
   or put `verificationStatus` on `/me`. After I submit my C of O,
   wondering for days whether it even arrived is the worst part of the
   whole flow.
2. **Fix the `agencyFee: 0` bug on `POST /listings`.** A solo owner not
   paying an agent is THE flagship use case. A 401 with no body for that
   payload makes the most loyal user (me) think their token broke.
3. **Build a real "my stuff" surface.** `GET /listings/mine` and
   `GET /properties/mine`. Right now my own property-management tool
   can't tell me what I own.
4. **Let me upload my document file directly.** Asking me to put my
   Certificate of Occupancy on a public image URL before submitting it is
   not safe and not realistic for the average 41-year-old.
5. **Ease the auth rate limit, or be transparent about it.** Getting
   429ed every time I log in for the second day in a row makes me feel
   like the app doesn't want me. Either widen the bucket for legitimate
   users, or expose `Retry-After` in the error body so the UI can show
   "try again in 50 seconds".
