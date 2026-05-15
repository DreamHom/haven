# DreamHomes Haven — persona audit summary

> Six personas, six Bruno collections, six independent reviews. Each agent
> only knew their own persona + the OpenAPI spec + the open internet —
> never the source code, never another persona's work. The findings below
> are what they independently surfaced, bucketed and ranked by cross-persona
> impact.

**Coverage**: 183 `.bru` files · 183 requests executed across 6 personas
running concurrently against `http://localhost:8080`. Full HTML reports per
persona in [`audit/reports/`](.). In-character reviews in
[`audit/personas/`](../personas/).

| Persona | Role | .bru files | Pass rate | Review |
|---|---|---:|---:|---|
| **Amaka** — Lagos Landlord | OWNER (solo) | 35 | 24/65 asserts | [amaka-review.md](../personas/amaka-review.md) |
| **Emeka** — Hustling Agent | AGENT | 22 | 24/27 asserts | [emeka-review.md](../personas/emeka-review.md) |
| **Temi** — First Timer | APPLICANT | 33 | 36/36 asserts | [temi-review.md](../personas/temi-review.md) |
| **Ngozi** — Skeptic | APPLICANT (rent-to-buy) | 23 | 31/31 asserts | [ngozi-review.md](../personas/ngozi-review.md) |
| **Biodun** — Developer | OWNER (multi-unit) | 32 | 42/42 asserts | [biodun-review.md](../personas/biodun-review.md) |
| **Dayo** — Platform Guardian | ADMIN (seeded) | 38 | 28/32 asserts | [dayo-review.md](../personas/dayo-review.md) |

---

## 🚨 Critical bugs — fix first, things are silently wrong

| ID | Finding | Personas | Severity |
|---|---|---|---|
| **B-1** | `POST /listings` with `agencyFee: 0` returns **401 with no body**. `cautionFee: 0` and `serviceCharge: 0` work fine. As a solo OWNER not paying any agent, `0` is the FLAGSHIP payload. Misleading 401 made the user think their JWT was bad. | Amaka | 🔴 |
| **B-2** | Sub-resources of a non-existent listing return **`200 []` instead of 404**. `GET /listings/1` → 404, but `/listings/1/photos`, `/slots`, `/comments`, `/reviews` all → 200 with empty arrays. UI renders empty galleries for ghost listings. | Temi | 🔴 |
| **B-3** | `GET /admin/<unknown-path>` returns **401 with a valid admin token** instead of 404. Spring Security's `/admin/**` catch-all fires before routing — endpoint-missing and auth-failed are indistinguishable. Caused every persona to think their session expired. | All 6 | 🟠 |
| **B-4** | `HEAD /listings` returns **401** while `GET /listings` returns **200** (public). Inconsistent — same path, same auth requirement, different answers. | Temi | 🟡 |
| **B-5** | `POST /auth/register` occasionally returns **401 instead of 202** around rate-limit boundaries. **Breaks the anti-enumeration contract** — a probe can detect "limiter is open" vs "registered" by status drift alone. | Emeka | 🟠 |
| **B-6** | Seeded admin credential is broken in any environment where the Postgres volume persists across boots — Flyway V11 doesn't re-run, so `ADMIN_PASSWORD_HASH` env-var changes have no effect on the stored hash. Dayo could not log in on first audit attempt; needed a manual DB UPDATE to fix. | Dayo (operationally) | 🟠 |
| **B-7** | `POST /admin/listings/{id}/approve` (re-publish path) has the **same name** as `POST /admin/verifications/{id}/approve` (verification approval). Two different "approves" with very different semantics. | Dayo | 🟡 |

---

## 🚫 Missing READ-side endpoints — the **biggest UX hole**

Every persona independently hit this. The API is heavily POST-oriented;
once you create something you can't see your own work.

### Submitter-side (every persona — universal frustration)

| Endpoint | Who needs it | What it costs |
|---|---|---|
| `GET /verifications/mine` | **Every persona** | Submit → black hole. Amaka panicked and double-submitted because she had no status page. |
| `GET /listings/mine` | Amaka, Biodun | Owner cannot see their own listings — only "remember the ID" or scroll public firehose |
| `GET /properties/mine`, `GET /properties/{id}` | Amaka, Biodun | Same — once IDs scroll off the create response, orphaned data forever |
| `GET /inspections/mine`, `GET /inspections/{id}` | Temi | No way to see upcoming inspections after booking |
| `GET /offers/mine`, `GET /offers/{id}` | Temi, Biodun | A missed notification = a permanently lost deal |
| `GET /listings/{id}/reports/mine` | Temi, Ngozi | After filing a scam report, no way to track it |

### Admin-side (Dayo's two CRITICAL gaps)

| Endpoint | Severity | What it costs |
|---|---|---|
| `GET /admin/audit-logs` | 🔴 **CRITICAL** | "Every other moderation guarantee on this platform is unfalsifiable." Self-audit before sensitive actions: impossible. Regulator paper trails: unanswerable. Compromised-admin detection: impossible. Re-reading own decisions: impossible. The audit log IS being written — there's just no reader. **Worst possible failure mode for a T&S system.** |
| `GET /admin/listing-reports` | 🔴 **CRITICAL** | User-facing `POST /listings/{id}/report` exists and persists rows. **No admin queue to drain them.** Reports go into a database no operator can see. Same write-only-moderation shape as the audit log. |
| `GET /admin/users?email=...` | 🟠 | Support tickets arrive with emails, not IDs. Dayo had to probe `/users/2`, `/users/3`, … to find a target. Not a workflow. |
| `GET /admin/users?suspended=true` | 🟡 | Can't audit own suspension hygiene ("accounts I left suspended >30 days") |
| `GET /admin/verifications` without mandatory `?type=` | 🟠 | Forces fan-out across 4 enum values to get a unified morning queue |

### Discovery / search — **kills the marketplace** (Biodun)

| Endpoint | What it kills |
|---|---|
| `GET /agents?q=...&verified=true` | 🔴 **Biodun cannot find Emeka to invite him.** `POST /listings/{id}/agent-assignment` takes an `agentId` integer — but no endpoint maps name/license → ID. Either call Emeka and ask him to log in and read his userId out, OR enumerate `/users/1/profile`, `/users/2/profile`... A delegation-first product where owners can't find agents is broken at the design level. |
| `?location=`, `?priceMin/Max=`, `?bedrooms=`, `?type=`, `?sort=` on `GET /listings` | Catalogue is unsorted, unfiltered firehose. **Worse: query params are silently ignored** — `?location=Yaba&priceMax=1000000` returns 200 with the unfiltered list, not 400. Temi thinks "no places in Yaba" when actually her filter was thrown away. |
| `GET /listings?hasAgent=false` | Emeka cannot prospect — no way to find listings without an agent |
| `GET /users/{id}/listings` | Temi (trust-following: "what else has this owner posted?") |

---

## 📜 Schema / contract drift — will burn frontend integrators

| Drift | Spec says | Reality | Personas |
|---|---|---|---|
| `LoginResponse` | `{ token, tokenType: "Bearer", expiresInSeconds: 3600 }` | `{ token }` only | Ngozi, Biodun, Dayo |
| `JwtPrincipal` from `/me` | Example shows `fullName` | Returns `userId, email, role, tokenVersion` — no `fullName` | Ngozi, Biodun, Amaka |
| `/me` field name | `userId` | But every admin write returns `id` | Dayo |
| `Listing.status` enum | Description text + example say `OPEN` | Schema enum + live response: `LIVE` | Amaka, Biodun |
| `AdminListingResponse.status` | `LIVE / PAUSED / CLOSED` | No `TAKEN_DOWN` value — collapses takedown vs closed distinction right where forensic clarity matters | Dayo |
| `RejectVerificationRequest.reason` | `minLength: 0` | Persona doc Story 3 says empty reason must 400. Schema lies. | Dayo |
| `RespondToOfferRequest.status` | Enum lists `PENDING / ACCEPTED / DECLINED / COUNTERED` | Only `ACCEPTED / DECLINED` are valid transitions — UI will build all 4 buttons and ship a bug | Temi, Biodun |
| `documentRefs` on verifications | `Map<String, Object>` with one example showing `{ kind, ref }` | Zero validation. Every submitter guesses a different shape. We will end up with 5+ conventions in production. | Every persona |
| `VerificationResponse.documentRefs` | Returns as `string` | But request takes `Map<String, Object>`. Does it round-trip through JSON.stringify? | Temi |
| `POST /listings/{id}/slots` description text | "Authorisation: the listing's owner (today). Assigned agent..." | **Sentence cut off mid-clause.** Can the agent open slots or not? Spec doesn't say. | Emeka |

---

## 🧱 Trust signal gaps — Ngozi closes the tab

Ngozi is the persona DreamHomes needs to win. She is the burned skeptic.
She found the platform structurally lacks the signals her persona doc
explicitly says she requires.

| Missing signal | Cost |
|---|---|
| `documentsVerifiedAt` on `ListingResponse` and listing index | The **single field a skeptic looks for before clicking**. Not on listing card, not on detail. Without it every listing reads "unverified" — exactly like the platforms that burned her. |
| `closedDealCount` on `PublicUserProfile` | "31 closed deals" is THE pitch for agents. Aggregate rating from 2 reviews means nothing. |
| `medianResponseMinutes` / `responseRate` on profile | Ngozi explicitly trusts agents who respond in <40 minutes through tracked channels. Platform records the data internally but never surfaces it. |
| `assignedAgentId` / agent block on `ListingResponse` | Skeptic can't follow listing → agent → reputation without a second call to `/users/{ownerId}/profile` and inferring who's actually managing the listing. |
| `reportCount` / "this listing has been reported" pill on listing detail | The next Ngozi needs to see the warning *before* she requests an inspection. |
| Sync notification on every user action | Across 9 days Ngozi got **0 notifications**. "Verification submitted" — silence. "Report filed" — silence. **"Silence is what scammers feel like."** |

**What genuinely thrilled Ngozi** (the platform's wins, document these):
1. `OFF_PLATFORM_FEES` is a **first-class enum** on the report reason — not buried in "OTHER". Designed-for-Lagos.
2. **No `feeAmount` field anywhere on inspection requests.** The protocol structurally cannot ask her for an fee. Design-as-trust.
3. `POST /listings/{id}/report` exists at all — her persona doc had it marked `⬜ Future`.
4. The 404 Problem+JSON shape (`{ type, title, status, detail, instance }`) is **honest and named** — tells her *what* was missing, not just "404".

---

## 🎬 Onboarding / UX pain — every persona felt this

| # | Pain point | Personas |
|---|---|---|
| **U-1** | `POST /auth/register` returns 202 with **no body, no JWT**. Every consumer fintech the personas use (Cowrywise, Carbon, Kuda, Opay) auto-logs in on signup. Forced re-login one minute after submission. | All 5 registering personas |
| **U-2** | `POST /auth/login` rate limit is **brutally tight** per IP. Multi-day flow runs were 429-blocked on the second day's first login. No `Retry-After` header in the 429 body. Two T&S operators on the same office NAT would block each other. | Amaka, Temi, Emeka, Biodun, Dayo |
| **U-3** | `LoginResponse` is just `{ token }`. No userId, no role echo, no expiresAt. Every persona had to make a second `/me` round-trip just to confirm what role the server thinks they have. | All authenticated personas |
| **U-4** | New account inbox is silent. No "Welcome to DreamHomes, your next step is X" notification. Empty page on first login looks like the platform forgot you registered. | Amaka, Ngozi |
| **U-5** | Anti-enumeration 202 with no body is technically correct, but **socially confusing** — "Did my account get created?" Personas couldn't tell from the response. Compromise: 202 body containing `{ "detail": "If this email is new, an account was created. Check your inbox or log in." }` keeps the contract and removes the ambiguity. | Amaka, Temi, Ngozi, Biodun, Emeka |
| **U-6** | `tokenVersion` is right there in `/me` — Amaka (a 41-year-old landlord) asked "what is this and why am I being shown it?" | Amaka |
| **U-7** | `POST /auth/logout` is **nuclear** — bumps tokenVersion so ALL devices get kicked out, not just the current. No "log out this device" option. | Amaka, Temi |

---

## 📂 Verification flow — broken for normal humans

Every persona uploads documents at some point — owner identity (Amaka,
Biodun), agent credentials (Emeka), applicant identity (Temi, Ngozi),
property documents (Biodun × 12). Every persona ran into the same gauntlet:

1. 🚫 **No file-upload endpoint.** Schema wants `documentRefs` as a JSON map. So the user must host their own NIN slip / C of O / agent license on a public CDN (imgbb, Cloudinary, Drive) and paste a URL. **No human is putting their Certificate of Occupancy on imgur.** This is dealbreaker UX for the most important trust flow on the platform.
2. 🚫 **`documentRefs` has no typed schema per verification kind.** Should it be `nin`? `nin_front`? `identity_doc`? `kind`/`ref`? Every persona guessed differently. The admin reviewer will guess too.
3. 🚫 **No `GET /verifications/mine`** — submission is fire-and-forget.
4. 🚫 **No way to amend a PENDING submission.** Blurry scan? Wait for admin to reject, then resubmit. Cold.
5. 🚫 **Submitted URL is not validated** — Temi submitted `https://res.cloudinary.com/dreamhomes-mock/...` pointing at a domain that doesn't exist; 201 came back. The contract has no reachability check.
6. 🚫 **No "verify development / block"** concept (Biodun). 12 units sharing one C of O = 12 identical `PROPERTY_DOCUMENTS` submissions, 12 identical admin decisions.

---

## 🏗️ Workflow primitives missing — kills developer-scale usage (Biodun)

| # | Gap | Fix |
|---|---|---|
| **W-1** | **No bulk operations anywhere.** Biodun has 12 units. Creating them = 12 × `POST /properties` + 12 × `POST /listings` + 12 × `POST /listings/{id}/agent-assignment` + 12 × multipart photo upload (one per photo per listing). ~48-60 manual calls for a single project. | `POST /properties/bulk`, `POST /listings/bulk`, `POST /agent-assignments/bulk`, `POST /listings/{id}/photos/bulk` |
| **W-2** | **No "duplicate listing / property" template call.** Same building, same shared marketing photos, identical units differing only by unit number — must be reentered from scratch. | `POST /listings/from-template/{id}` |
| **W-3** | **No `Development`/`Block` parent entity.** Every unit is freestanding; applicants see 12 lookalike listings with no visible "this is part of a 12-unit project". | Add `developmentId` foreign key on Property, with a Development entity that owns one C of O. |
| **W-4** | **No decline reason / message** on `PATCH /offers/{id}` declines. Cold for the applicant. | Add optional `reason` to body |
| **W-5** | **`POST /offers/{id}/respond` ACCEPT doesn't auto-close the listing.** If owner forgets `PATCH /listings/{id} { status: CLOSED }`, the listing stays LIVE and keeps receiving new offers nobody can accept. | Either auto-close, or respond with a banner-prompt flag the frontend can act on |
| **W-6** | **No `mark-all-read`** on notifications. Single-row only. | `POST /notifications/mark-all-read` |
| **W-7** | **No notification `?type=` filter.** Biodun cares only about `OFFER_SUBMITTED`; everything else is noise. | `GET /notifications/mine?type=OFFER_SUBMITTED` |
| **W-8** | **No `DELETE /inspections/{id}` (cancel) or `DELETE /offers/{id}` (withdraw).** Submitted = locked in forever. Temi can't cancel if something comes up at work. | Both, gated to PENDING state |
| **W-9** | **No reason on `reactivate-user` and `re-publish-listing`** (Dayo). Symmetric write needs symmetric record — every audit-trail reversal action should require justification. | Add optional `reason` body to both |

---

## 🔔 Notifications — design is half-built

| Gap | Impact | Persona |
|---|---|---|
| Zero sync notifications on user actions (verification submitted, offer submitted, report filed, …). Inbox empty after 9 days of activity. | "Silence sounds like a scammer." | Ngozi (loudest), Temi |
| `payload` is a free-form `string` not a typed object | Every UI must JSON.parse + switch on kind. No schema guarantees. | Temi |
| No `?type=` filter | Can't focus on offers only | Biodun, Emeka |
| No `mark-all-read` | One-at-a-time tapping on 12-unit-developer scale | Biodun, Temi |
| No real-time push (SSE / WebSocket / webhook) | User must refresh; mid-negotiation latency is high | Temi |
| No notification preferences endpoint | Can't opt out of specific kinds | Temi |
| No `Last-Modified` / `ETag` on `/notifications/mine` | Re-downloads full page every poll on a 3G phone | Emeka |
| `unread-count` is a single integer with no per-type breakdown | Can't tell from the count whether to bother opening the app | Biodun |

---

## 🔐 Privacy / security

| Finding | Severity | Persona |
|---|---|---|
| **Admin account publicly visible** at `GET /users/1/profile` — returns `{ role: ADMIN, fullName: "Platform Admin" }` to anonymous callers. Admin-account enumeration. | 🟠 | Temi |
| **Anti-enumeration register contract is fragile** — under rate-limit boundary, register can return 401 instead of 202. Probe can detect when the limiter is open. | 🟠 | Emeka |
| **Logout invalidates ALL devices** by default — no "this device only" option. | 🟡 | Amaka, Temi |
| **Verification documents hosted externally by users** — NINs and C of Os on public CDNs anyone with the URL can scrape | 🔴 | Every persona |
| **Submitted document URL is not validated** for reachability or HTTPS or content-type. | 🟡 | Temi |
| **No admin override / allowlist on auth rate limit** — admin can be locked out by sharing an IP with a user. | 🟡 | Dayo |

---

## 🏢 Domain modeling — gaps that pinch real users

| Missing concept | Persona impact |
|---|---|
| `intent: enum [RENT, BUY, RENT_TO_BUY]` on `SubmitOfferRequest` and `OfferResponse` | Ngozi's whole reason for using the platform is rent-to-buy with Moniepoint. Currently has to cram it into the `message` free-text field, which is "not a contract — it's a hope." |
| `Property.type` enum lacks `SELF_CONTAIN`, `MINI_FLAT`, `STUDIO`, `ROOM_AND_PARLOUR` | Lagos starter-unit vocabulary missing. Temi searching for self-cons would find them filed under generic `APARTMENT`. |
| `Property` lacks `floor`, `parkingSpaces`, `serviced`, `generatorIncluded`, `yearBuilt`, `gated` | Amaka had to stuff all of this into the address/description free-text. Lagos applicants filter by these. |
| `Listing` lacks `title`, `description`, `headline`, `handoverDate`, `promo` | Off-plan developer launches and one-off resales look identical. Biodun can't differentiate his Ojodu units. |
| `InspectionSlot` lacks `capacity`, `notes`, `address pin`, `arrival mode`, `agent assigned to this slot` | Emeka can't tell applicants "ring bell #3" or "use side gate" without WhatsApping each one individually. |
| `Offer` lacks `moveInDate`, `leaseLengthMonths`, `conditions`, `paymentMethod` | All of it lives in free-text `message`. Disputes a month later: "I thought you said move-in was 1st…" |
| `Comment` schema has parent-child support in model but no `parentId` field on the POST request | "Reply to comment" feature looks built but isn't wired through. |
| No agent/owner separation on review writes — `revieweeUserId` is required | Temi must lookup the user ID of "the person I rented from"; she can't leave separate reviews for the agent vs the owner |

---

## 💸 Cross-cutting "tiny but matters" findings

| Finding | Persona |
|---|---|
| 401 responses have **no body** (no Problem+JSON envelope). 404 does. Mixed silence is the worst. | Ngozi, Temi |
| 429 (rate limit) has no `Retry-After` header. "Try again in N seconds" can't be shown to user. | Temi, Amaka |
| `DELETE /comments/{id}` and `DELETE /reviews/{id}` require a `reason` body **even for self-delete**. Author shouldn't owe the platform a justification for removing their own content. | Temi |
| Listing photo upload is **one file per request**. Six photos = six round trips on a Glo connection. | Amaka, Biodun |
| No file size / dimension hints in the spec for photo upload. Flying blind on what's allowed. | Amaka |
| No "alert me on price change" toggle on `POST /listings/{id}/save`. | Temi |
| No "how many people saved my listing?" inverse on `GET /saves/mine`. Pricing-signal gold for owners. | Amaka |
| `displayName` accepted on register but not surfaced on profile schema | Emeka |
| `decisionReason` field on `AgentListingResponse` populates only on decline. If accept could carry a thank-you note, it'd live there too. | Emeka |
| `PATCH /listings/{id}` body only accepts `askingPrice` and `status`. **Property is immutable** once created (no PATCH on `/properties/{id}` either). Typo in the address = stuck forever. | Amaka |

---

## ✅ What genuinely worked — credit where it's due

Across all 6 reviews, multiple personas called out:

1. **JWT-based auth + `/me` round-trip is fast.** No DB read on `/me` (Biodun, Dayo).
2. **Anti-enumeration register is the right call** for security (Biodun, Ngozi, Emeka — though all wanted better copy).
3. **Photo upload via multipart accepts real JPEGs** and returns 201 (Biodun, Amaka).
4. **Counter-offer `parentOfferId` chain is a clean model** (Biodun).
5. **PATCH listing status transitions are state-machine validated** — re-opening a CLOSED listing correctly returned 409 (Amaka).
6. **`POST /listings/{id}/save` is idempotent** — saving twice doesn't error; un-saving an un-saved listing returns 204 (Temi).
7. **Slot overlap protection works** — second slot at the same window correctly 409s (Amaka).
8. **`Cache-Control: public, max-age=60`** stamped on `GET /listings` — saves Temi's MTN data plan.
9. **404 Problem+JSON is honest and named** — `"Listing 17 was not found"` not generic 404 (Ngozi).
10. **Verification duplicate guard works** — second PENDING of same type → 409 (Amaka, Emeka).
11. **Suspension lifecycle is wired end-to-end** — suspend → 409 on re-suspend, reactivate → 409 on re-reactivate, **403 on self-suspend** (Dayo).
12. **`OFF_PLATFORM_FEES` first-class enum** + **no `feeAmount` anywhere on inspections** = design-as-trust for the Lagos scam vector (Ngozi — "Whoever wrote this knew about Lagos.").
13. **`POST /listings/{id}/report` ships** — persona doc had this marked `⬜ Future` (Ngozi).
14. **Takedown → 404 on public discovery, reversible via re-publish** — confirmed end-to-end (Dayo).

---

## 📋 Per-persona "Top 5 to fix tomorrow" — ranked

| # | Amaka | Emeka | Temi | Ngozi | Biodun | Dayo |
|---|---|---|---|---|---|---|
| 1 | `GET /verifications/mine` | `closedDealCount` + `responseRate` on profile | Make `GET /listings` actually filterable | `documentsVerifiedAt` on every listing card | `GET /listings/mine` with engagement counts | `GET /admin/audit-logs` |
| 2 | Fix `agencyFee: 0` 401 bug | `?status=` filter on `agent-listings/mine` | Build all the `GET /...mine` read-sides | `closedDealCount` + `medianResponseMinutes` on profiles | `GET /agents?q=...&verified=true` directory | `GET /admin/listing-reports` |
| 3 | `GET /listings/mine` + `GET /properties/mine` | "Listings without an agent" prospecting feed | File upload + typed `documentRefs` per verification type | Sync notifications on every user action | `GET /offers/mine` | Unified `GET /admin/verifications?status=PENDING&sort=oldest` |
| 4 | File upload for verification documents | `POST /verifications/{id}/files` + `GET /verifications/mine` | Auto-JWT on register + DELETE for inspections/offers | `intent` enum on offers (RENT/BUY/RENT_TO_BUY) | Bulk create endpoints + `Development` entity | `reason` field on reactivate/republish + `minLength: 1` on reject |
| 5 | Ease `/auth/login` rate limit + `Retry-After` | Fix the cut-off spec text on `POST /listings/{id}/slots` + agent slot authority | Plain-English fee glossary + all-in cost on listings | "Speak to the user" — every action gets a sync notification | Listing status enum fix (LIVE/OPEN) + `description`/`headline`/`handoverDate` | `GET /admin/users?email=...` search |

---

## 🎯 If you only fix five things from this entire audit

Ranked by how many personas were hurt and how badly:

1. **Build the read-side.** `GET /verifications/mine`, `/listings/mine`, `/properties/mine`, `/inspections/mine`, `/offers/mine`. **Every single persona** independently named this as their #1 or #2 frustration. Cost: 5 endpoints. Value: removes the "submit-into-a-void" experience that's currently the worst part of the platform.

2. **Ship `GET /admin/audit-logs` and `GET /admin/listing-reports`.** Same write-only-moderation bug appears in two independent features. The data is already being written. The reader is the entire trust contract with users, regulators, and Dayo's own peers. **Without this, every other moderation guarantee is unfalsifiable.**

3. **Build the agent-discovery surface** (`GET /agents?q=...&verified=true`). Delegation is the platform's stated differentiator. Currently owners cannot find agents to invite. The marketplace cannot form.

4. **Ship a real file-upload endpoint** for verification documents + a typed `documentRefs` schema per `type`. Asking real users to host their NIN on Cloudinary is a non-starter — the trust flow literally cannot complete.

5. **Fix `agencyFee: 0` → 401, the empty-filter-silent-200 on `GET /listings`, and the sub-resources-of-missing-listing 200-empty-array.** Three confirmed correctness bugs that misdirect UI behaviour and waste user trust.

The first 4 are net-new endpoints worth maybe a day of work each. The 5th
is fixes within existing endpoints. Everything else in this document is
ranked below these.

---

*Generated from 6 independent persona walkthroughs run concurrently against
the live API. None of the agents read source code, tests, migrations, or
each other's work — only the OpenAPI spec, their persona doc, and the open
internet. Findings are what the contract + the running behavior actually
showed them.*
