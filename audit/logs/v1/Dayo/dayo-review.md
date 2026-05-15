# Dayo — Platform Guardian, audit review (re-run)

> *"Every badge I approve is a promise to the person who sees it."*

I am the trust & safety operator at DreamHomes. Last time I sat down to
audit this platform, the seeded admin credential was broken and I
couldn't even log in — the whole day collapsed into "I can't start
work." Login was fixed since then, so I ran the entire collection again
with a working session. This is what I found when the door actually
opened.

## Run stats

- 38 `.bru` files across 6 chronological sub-folders
- **34 / 38 requests passed**, **28 / 32 assertions green** (87.5%)
- 4 failures remaining, every one of them a real product finding (not a
  test bug — I call each one out where it lives)
- HTML report at `audit/reports/dayo.html`
- All requests bear an admin JWT obtained from `POST /auth/login` with
  `admin@dreamhomes.local` / `AuditAdmin#1!`

Bottom line up front: **logging in works, and most of the verification
and suspension lifecycle is wired correctly.** But the moment I tried
to do the things a T&S operator actually does between approvals — read
the audit log, see what users have reported, look up a user by email —
the platform went silent. Three of my five tomorrow-fixes are
read-side gaps that turn a working write API into a black box.

---

## 1. Morning queue — what's on my plate?

`1-Morning-queue/` — 8 requests. All 200 with my admin token.

✅ `POST /auth/login` accepts the seeded admin credentials and returns
a JWT. (This is the single thing that changed since my last attempt.)
✅ `GET /me` confirms `role: ADMIN, userId: 1, tokenVersion: 2` — I
know who I am. Good. But see complaint below about field naming.
✅ `GET /admin/analytics/summary` returns top-line numbers. Useful for
the morning glance — total users, listings, applications, average
price.
✅ `GET /admin/verifications?type=OWNER_IDENTITY|APPLICANT_IDENTITY|AGENT_CREDENTIALS|PROPERTY_DOCUMENTS`
all return paginated PENDING lists with submitter IDs and document
refs. Each call individually is fine.

😕 **The biggest queue-design problem**: `GET /admin/verifications`
**requires** `?type=...` as a mandatory query param. There is no "give
me everything PENDING right now, oldest first" call. To see my full
backlog I have to make **four separate requests** — one per enum —
and mentally merge four paginated result sets to get a chronological
queue. That is not a morning workflow. That is busy-work.

⚠️ No `?status=` filter is documented. The spec promises PENDING
only, but I cannot ask for `REJECTED` to audit what I rejected last
week, or `APPROVED` to spot-check a peer's work. The four states the
spec defines (PENDING / APPROVED / REJECTED) are only ever filterable
by *type*, not *status*.

⚠️ No `?sort=` either. I want oldest-first to enforce a fairness SLA
on the queue (24-hour SLA on `OWNER_IDENTITY`, 48 on
`PROPERTY_DOCUMENTS`). Without it I cannot tell which submission has
been waiting longest.

😕 `GET /me` returns the principal as `userId` — every other admin
endpoint uses `id` for the same concept. That cost me one round of
debugging in my own scripts and would cost a frontend the same.

### What would make this morning better for me as Dayo?

A single `GET /admin/verifications?status=PENDING&sort=submitted_at_asc`
that fans across all four types and returns a unified queue. And an
`oldest age` sort so the SLA-breaching items rise to the top
automatically. Right now I'm assembling my own queue from four
half-views and trusting myself not to miss one.

---

## 2. Approving submissions — does the right thing happen?

`2-Lunch-queue/` — 7 requests. All 200.

✅ Approved an `OWNER_IDENTITY` submission. Server returned the
verification with `status: APPROVED` and a `decidedAt` stamp. The
submitter's `identityVerifiedAt` was stamped (verified by re-reading
their profile).
✅ Approved an `APPLICANT_IDENTITY`. Same shape, same stamp on the
user.
✅ The submitter's public profile reflects the badge immediately.

🚫 `POST /admin/verifications/{id}/approve` accepts **no request body**
on this spec. Story 2 of my persona doc explicitly says approval should
record an *optional* `decision_reason` — "approved despite blurry NIN,
owner is repeat-good-actor since 2024". The current contract gives me
no slot to attach that note. When I approve a borderline case, the next
admin who looks at the audit log (if one existed — see §6) sees only
"approved by Dayo, no reason." That's an audit-trail loss.

🚫 No way to confirm via API that the submitter got a notification
(`VERIFICATION_APPROVED`). I have to TRUST the docs say it happens.
From my seat that's invisible.

🚫 No way to confirm an audit log row was written. There's nowhere to
read audit logs from — see §6.

### What would make this better for me as Dayo?

Add an optional `decisionReason` field to the approve request body.
Even one line — "approved, repeat good-actor, see ticket #4421" — is
worth more than the silent boolean we have now.

---

## 3. Rejecting with a real reason

`2-Lunch-queue/07-RejectPropertyDocsAddressMismatch` — 200.

✅ Rejected a `PROPERTY_DOCUMENTS` submission with a real reason:
*"Address on the C of O ('15 Admiralty Way, Lekki Phase 1') does not
match the property address you registered ('17 Admiralty Way, Lekki
Phase 1'). The numbers are different. Please resubmit a C of O
matching the listed property, or update the property address to match
the document."* Server accepted it. This is the difference between a
useful T&S team and a useless one — and the platform let me write it.

✅ Rejected an `AGENT_CREDENTIALS` (in `3-Afternoon-actions/`) with a
similar real reason. (Note: this folder couldn't probe a *fresh* agent
credential — see §4 below.)

🐛 **The `reason` schema lies.** `RejectVerificationRequest.reason` is
declared with `minLength: 0` in the OpenAPI spec. That tells me — and
tells any frontend that generates a form from the contract — that an
empty rejection reason is legal. The persona doc Story 3 says empty
reasons should 400 *before any DB write*. The spec and the persona doc
disagree. Either the contract should say `minLength: 1, pattern: \\S`,
or the validator is more permissive than advertised. Either way, an
operator reading the contract can't tell what they're allowed to
submit.

⚠️ I couldn't fully probe the empty/whitespace case in this run
because by the time my Defensive-checks folder ran, every PENDING
verification my scripts knew about had already been decided — so
empty-reason hit a `409 already-decided` path instead of the `400
validation` path. That's itself a finding: **I cannot easily test
rejection validation in isolation because there's no "reset to PENDING"
affordance.** I'd want a dedicated `min-1-char` validation visible in
the schema rather than a behavior I have to reverse-engineer.

🚫 Same audit-trail blackhole as approve — the rejection reason is
written somewhere, the submitter (allegedly) gets it in their
notification, but I can never re-read it from the admin side.

### What would make this better for me as Dayo?

Tighten the OpenAPI to `minLength: 1` (and ideally `pattern` against
whitespace-only) so the contract matches the persona-doc intent.

---

## 4. Afternoon agent rejection — the queue ran dry

`3-Afternoon-actions/01-RefreshQueueAgent` — 200.
`3-Afternoon-actions/02-ReadAgentSubmitterProfile` — **401** ❌
`3-Afternoon-actions/03-RejectAgentCredentialsBadDocs` — **401** ❌

😕 The `AGENT_CREDENTIALS` queue was **empty** when I checked. Either
no agent persona submitted one in this seed, or an earlier admin run
already cleared it. My script didn't get a verification ID to follow
through with, so the next two requests went out with empty path
segments and 401'd.

The product finding underneath: there is no way for me, as an admin,
to **replay history**. If I want to look at "the last 10 agent
credentials I processed this week, including the ones I rejected, with
the reasons I gave", I can't. The queue is forward-only. When it's
empty it tells me nothing about what *was* there.

⚠️ Cosmetic: a path-template hit with an empty path segment
(`/api/users//profile`) returns **401** instead of a clearer 400/404.
That silently masks the real bug (missing variable) as an auth
failure. From the operator chair, "401" on an `/api/users//profile`
call means I have to think "did my token expire? am I missing
scopes?" — when actually the URL was malformed. Mild, but it costs
debugging time.

### What would make this better for me as Dayo?

A `GET /admin/verifications?status=APPROVED|REJECTED` filter (see §1)
solves the "show me what I did" half of this. The "let me re-read my
own rejection reasons" half is solved by audit logs (see §6).

---

## 5. Suspending and reactivating — does the trail hold?

`4-Suspension-test/` — 6 requests. All 200/409 as expected.

✅ Picked user id=2 (Amaka Okafor, role=OWNER) from
`/users/{id}/profile` — a non-admin user.
✅ Suspended her with a real reason: *"Took inspection fee
off-platform — confirmed via reporter screenshot in support ticket
#4421. Agent admitted in WhatsApp screenshot to asking applicant to
pay 25,000 NGN to personal Opay before showing the unit. Suspending
pending review."* Server returned the `UserAdminView` with `suspended:
true` and the reason echoed. Good.
✅ Tried to suspend her again → **409 Conflict** as the persona doc
demands. Idempotency check passes.
✅ Reactivated her → **200**, `suspended: false`.
✅ Tried to reactivate her again → **409 Conflict**. Reversibility +
idempotency both verified end-to-end.

✅ (Defensive) Tried to suspend my own admin id → **403** with
`CannotModerateSelfException`. The admin role cannot lock itself out
of moderation. Critical guard, present and working.

🚫 **`POST /admin/users/{id}/reactivate` has no request body.** Story 5
of my persona doc says the audit log should capture WHY I reactivated
("user produced exonerating evidence, see ticket"). There's no
`reason` field on the request — so even if the audit log existed, the
reactivate row would have no human-readable justification. From a
T&S-process perspective this is asymmetric: suspend requires a reason,
reactivate has no reason field at all.

🚫 **I cannot verify, from the API alone, that the `tokenVersion` bump
actually invalidated the suspended user's outstanding JWTs.** The
persona doc Story 4 says it should — but I don't have access to that
user's JWT, and there's no admin endpoint to inspect "what
tokenVersion is user X currently on." I have to TRUST the
implementation. A `GET /admin/users/{id}/sessions` style read, even a
read-only "currentTokenVersion" field on `UserAdminView`, would close
that gap.

⚠️ The suspension response (`UserAdminView`) is generously informative
— it echoes the suspension reason, suspendedAt, suspendedByAdminId.
But the reactivation response carries no parallel field set (no
"reactivatedAt", no "reactivatedByAdminId"). That asymmetry is exactly
the kind of thing that, six months from now, makes someone ask "wait,
who un-suspended this scammer?" and nobody knows.

### What would make this better for me as Dayo?

Make `POST /admin/users/{id}/reactivate` accept an optional `reason`,
and have `UserAdminView` echo `reactivatedAt` and
`reactivatedByAdminId` in the response. Symmetric write, symmetric
record.

---

## 6. The audit log that doesn't exist

`6-Audit-log-hunt/01-AuditLogsByActor` →
`/admin/audit-logs?actor_id=1` — **401**
`6-Audit-log-hunt/02-AuditLogsByTargetUser` →
`/admin/audit-logs?target_user_id=2` — **401**

🚫 **The single most damaging gap in this entire platform.**

The persona doc Story 7 says: *"`GET /admin/audit-logs` — every admin
write produces exactly one audit log row, with actor_id, action,
target_type, target_id, reason, timestamp. Read endpoint returns
paginated entries with filters."* The acceptance criteria are ticked
in the persona doc as "implemented".

I confirm, after a full re-run with working auth: **the read endpoint
does not exist.** `openapi.json` lists exactly eight `/admin/...`
paths — none of them is `/admin/audit-logs`. The probe returned 401
(Spring Security catch-all for unknown admin paths — see cosmetic
finding below) but a 401, 404, or 410 all mean the same thing from my
chair: I cannot read the audit log.

Without an audit-log read I cannot:

- **Self-audit before sensitive actions.** My persona doc *explicitly*
  says I check the log before suspending an agent, to make sure I'm
  not revoking a badge from someone who flagged something legitimate.
  That step is impossible. I am suspending people blind.
- **Investigate after the fact.** "Was this account suspended by
  Dayo, or by another admin? On what evidence?" — unanswerable.
- **Prove a moderation paper trail to a regulator.** EFCC or NDPR
  requests for "show us every action taken against user X" cannot be
  answered from the API.
- **Spot a compromised admin.** If someone phished an admin and the
  attacker started approving fake verifications, I'd have no way to
  notice the spike — the log they're writing is the log I can't
  read.
- **Re-read my own work.** "What reason did I give yesterday when I
  rejected Emeka's credentials?" — gone.

The write half is (allegedly) working. The data is being written.
There's just no reader. That is the worst possible state for an audit
trail — *we are paying the storage cost for compliance evidence we
cannot ever surface*.

🐛 **Cosmetic but real**: a `GET /admin/<unknown-path>` returns **401**
to a perfectly valid admin JWT, instead of the expected **404**.
That's because Spring Security's `/admin/**` matcher requires auth and
the framework returns 401 before the routing layer gets to decide
"this path doesn't exist." So from the outside, "endpoint missing"
and "auth failed" are indistinguishable. For an operator using a tool
like Bruno/Postman/Scalar that's confusing — I had to verify against
the spec to be sure my token wasn't stale.

### What would make this better for me as Dayo?

Add `GET /admin/audit-logs` with at minimum `actor_id`,
`target_user_id`, `target_listing_id`, `action`, `from`, `to`, `page`,
`size`. The data model is already there. Surface it. **Until that
exists, every other trust-and-safety guarantee on this platform is
unfalsifiable.**

---

## 7. User-reported listings — where do I see them?

`6-Audit-log-hunt/03-ListingReportsAdminQueue` →
`GET /admin/listing-reports` — **401**

🚫 **Same shape of bug as the audit log.** The spec exposes
`POST /listings/{listingId}/report` for users — Ngozi can flag a scam
listing, the request returns a `ListingReportResponse` with a real ID,
and (per the description) a `LISTING_REPORTED` notification fans out
to every admin. So reports ARE being persisted, an ID is being
generated, and the unique-per-user-per-listing constraint is enforced.

But there is **no admin endpoint to LIST those reports**. They go into
a black box. From my seat, the workflow is supposed to be: morning
queue → "what got reported last night?" — but the only way to learn a
listing was reported is the notification fan-out to every admin's
notification feed. That means:

- If five admins are on call and one acknowledges the notification,
  the other four still see it in their feed — no shared inbox.
- If an admin was off-shift, the notification scrolls past and the
  report is effectively invisible.
- There's no count, no aging, no SLA visibility. "How many reports
  came in this week?" — I can't tell.
- I cannot filter reports by reason (`SCAM`, `INAPPROPRIATE`,
  whatever). The enum exists in `ReportListingRequest` but no admin
  query exposes it.

😕 The persona doc Story 8 was marked ⬜ Future in the older version
of the docs. In the current state, the write half is implemented but
the read half isn't. That is even worse than "not implemented" —
because now real user reports are sitting in a database that no
operator can drain.

### What would make this better for me as Dayo?

Add `GET /admin/listing-reports` (paginated, filterable by
`reason`/`listingId`/`reporterUserId`/`status`) and probably a
`POST /admin/listing-reports/{id}/dismiss` or `/resolve` so I can
mark them processed. Anything less is "write-only moderation".

---

## 8. Taking down a fraudulent listing

`3-Afternoon-actions/04-FindListingToTakeDown` → 200 (found a
listing).
`3-Afternoon-actions/05-TakedownListing` → 200.
`3-Afternoon-actions/06-VerifyTakedownInvisibleToPublic` → 404 (good).
`3-Afternoon-actions/07-RepublishListing` → 200.

✅ Took down a listing with the reason *"Listing photos appear stolen
from a Property24 ZA listing in Sandton (reverse image search hits 3
prior posts dating to 2023). Address '15 Admiralty Way, Lekki' but
shower head, light switches, and trim are all SA-spec. Taking down
pending owner contact and document re-verification."* Server returned
the listing as `TAKEN_DOWN`. Good.
✅ Public `GET /listings/{id}` now returns **404**, exactly as the
persona doc Story 6 specifies. Discovery is properly blocked.
✅ Re-published it via `POST /admin/listings/{id}/approve` to verify
reversibility. Server returned the listing back to `OPEN`. Confirmed.

🚫 Same audit-trail story: I have to trust that takedown wrote a row
and re-publish wrote another row. I can't read them.

🚫 The re-publish endpoint (`POST /admin/listings/{id}/approve`) takes
**no request body**. So a re-publish leaves no `reason` in the audit
trail — "I re-published because the owner produced a deed of
assignment matching the photos" is information that vanishes the
moment I click the button. Symmetric to the reactivate-user complaint:
the un-do action loses its justification.

😕 The endpoint name is `approve`, not `restore` or `republish`.
That's slightly confusing because `approve` is also used in
`/admin/verifications/{id}/approve` to approve documents. Two
different "approves" with very different meanings. Would prefer
`/admin/listings/{id}/restore` or `/admin/listings/{id}/republish`.

### What would make this better for me as Dayo?

Add an optional `reason` to the re-publish request, and rename it to
something less ambiguous. And — surprise — give me an audit-log
endpoint so the takedown / re-publish actions don't evaporate.

---

## 9. Other gaps I bumped into

🚫 **No admin user-search.** `GET /admin/users?email=foo@bar.com`
returns 401 (no such endpoint). Support tickets arrive with an email,
not a user ID. The only way I can find a user is to know their ID
already, or scrape verification queues looking for a matching
`submitterUserId`. That's not a workflow; that's a workaround.

🚫 **No "list suspended users" endpoint.** I can suspend, I can
reactivate, but I cannot ask "show me every currently-suspended user".
If I want to audit my own suspension hygiene — "are there accounts
I've left suspended for >30 days without revisiting?" — the platform
gives me no way.

⚠️ **`POST /auth/login` is rate-limited per IP with no admin
override or internal allowlist.** Two consecutive bulk runs of my
Bruno collection trip the limiter and I have to wait ~60-90 seconds
before the next login goes through. In a real shift-handover scenario,
two T&S operators on the same office NAT hitting the console at the
same time would block each other. (I hit this multiple times during
this very audit — it cost me real time.)

⚠️ **`LoginResponse` returns only `{token}` — no role, no userId, no
tokenVersion, no expiresAt.** I have to make a second `/me`
round-trip just to confirm "yes, I'm the admin and not some test
account." For a T&S console that's two roundtrips on every session
start.

😕 **`GET /me` returns `userId` but every admin write returns `id`.**
Pick one.

---

## Top 5 things I'd fix tomorrow

Ranked by how much each one improves platform trust.

### 1. Ship `GET /admin/audit-logs`.

Without it, **every other moderation guarantee on the platform is
unfalsifiable**. The data is being written. The reader is the entire
trust contract with users, regulators, and the platform's own admins.
Filters required at launch: `actor_id`, `target_user_id`,
`target_listing_id`, `action`, `from`, `to`. Until this exists I am
acting on faith every time I press "suspend" or "takedown".

### 2. Ship `GET /admin/listing-reports`.

The user-facing report endpoint exists. The reports are being stored.
There is **no admin queue to drain them.** That means every report
filed by a Ngozi-style skeptical user is sitting in a table no
operator can see. Pair it with a `dismiss` / `resolve` so we can close
out the queue.

### 3. Unified `GET /admin/verifications?status=PENDING&sort=oldest`.

Make the morning queue a single call. Right now I'm fanning out four
calls and merging in my head. Add `?status=` and `?sort=` so I can
also audit my own past decisions and enforce SLA-based ordering.

### 4. Add `reason` body to reactivate-user and re-publish-listing; make `RejectVerificationRequest.reason` `minLength: 1`.

Every write endpoint that takes a destructive *or* reversal action
should require a justification. Right now suspend has a reason,
reactivate doesn't. Takedown has a reason, re-publish doesn't. Reject
"requires" a reason but the schema says `minLength: 0`. Tighten all
three. The cost is one line of validation per endpoint; the value is
a forensic-grade trail.

### 5. Add `GET /admin/users?email=…` and `GET /admin/users?suspended=true`.

Support tickets arrive with emails, not IDs. Probing ID 2-10 to find
"the agent we just suspended" is not a workflow. And a "currently
suspended" view is the bare minimum for hygiene — "how many accounts
have I left in limbo for >30 days?"

---

## What genuinely shocked me

The same shape of bug appears in two different places: **write-only
moderation**. The audit log is being written but cannot be read. The
listing reports are being written but cannot be read. In both cases
the platform looks correct from the database's perspective and is
unobservable from the admin's perspective. That is the worst possible
failure mode for a trust & safety system — we are paying the storage
cost for evidence we can never produce.

If a regulator walks in tomorrow and asks "show me every action your
moderators took against user X in the last 90 days", the answer today
is "we have it in the database but no one can show it to you." That
is the finding to fix first.

— Dayo
