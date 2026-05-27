# Post-Session Tasks

Work queued while we walk through the deep-dive sessions. Pick up after Session 10 is done (or sooner if a session ends early).

Status legend: 🟢 in flight · ⏸️ paused · 📋 pending · 🚨 urgent before May 26 · ✅ done

---

## 🟢 1. Caffeine in-process cache (in flight)

Adding Spring Cache + Caffeine on the 3 hottest reads (`ListingService.findPubliclyVisible`, `ListingService.browsePublic`, `UserProfileService.publicProfile`) plus `@CacheEvict` on the matching writes. TDD-first, `mvn verify` green at end.

**Why:** rubric explicitly lists "caching" under Functional Completeness (15%). HTTP `Cache-Control` alone may not satisfy judges; in-process cache gives a visible answer.

**Outcome doc:** appends to `docs/TRADEOFFS.md` (Caffeine over Redis at this scale, when to revisit).

**Agent will notify when complete.** No action needed mid-session.

---

## ⏸️ 2. Pre-signed R2 upload URLs (paused — re-launch after Session 10)

Add a two-step pre-signed upload path alongside the existing multipart-proxy upload:

1. `POST /api/listings/{id}/photos/upload-url` → mints 10-min TTL pre-signed PUT URL + creates `photo_upload_intent` row
2. `POST /api/listings/{id}/photos/confirm` → HEADs R2 to verify upload, creates `listings_photos` row

Keep existing multipart endpoint — Vista migrates gradually.

**Includes:**
- Flyway migration `V41__photo_upload_intent.sql`
- Hourly `@Scheduled` cleanup of expired intents
- OpenAPI annotations on both new endpoints
- `docs/vista/photo-presign-frontend-prompt.md` — comprehensive frontend integration brief (TypeScript types, working pseudocode, every error code, edge cases, migration story)
- `docs/photo-upload-architecture.md` — proxy-vs-presign trade-off explainer
- `docs/TRADEOFFS.md` entry

**Demo answer it unlocks:**
> "Photo uploads currently proxy through our API, which works fine at our scale. We've shipped a parallel pre-signed flow that lets the browser upload directly to R2 — Vista is migrating to it screen-by-screen so we can scale to >100 concurrent uploads without bandwidth on the API."

---

## 📋 3. AWS / Railway profile scaffolding (pending Silas sync)

Once Silas confirms the AWS specifics, create:

- `src/main/resources/application-aws.yml` — RDS + MSK env-var bindings
- `src/main/resources/application-railway.yml` — bundled PG + Confluent
- Set default activation in `application.yml`

**Need from Silas:**
1. Compute platform (ECS Fargate? EKS? EC2 + ALB?)
2. RDS connection style (`DATABASE_URL` vs split `DB_HOST/PORT/USER/PASS`?) + auth (IAM or password?)
3. MSK bootstrap endpoints + auth mechanism (IAM, SCRAM-SHA-512, mTLS?)
4. Secrets management (Secrets Manager, Parameter Store, env vars?)
5. Profile naming preference (`aws` / `railway` vs `prod` / `local`?)

---

## 📋 4. Demo presentation outline

Draft the Problem → Solution → Demo → Conclusion narrative for demo day. This is separate from the technical sessions and addresses rubric category **Structure & Flow (10%)**.

**Sections to draft:**
- 30-sec opening: the problem DreamHomes solves
- 2-min architecture: the one-slide explainer
- 6-min live demo: which features, in what order, with timings
- 1-min innovation hook: Dream AI + Nigeria-specific trust signals
- 1-min what's next: roadmap / what we'd build with another month

**Output:** `docs/demo-prep/presentation-outline.md` + speaker notes.

---

## 📋 5. End-to-end demo dry-run

Test the full Vista + Haven flow against production:

- Register flow (Amaka → owner)
- Publish a listing with photos
- Search + browse from Temi's POV
- Save + comment + inspection request
- Submit + accept an offer
- Admin moderation cycle (Dayo)
- Dream AI rank + compare turns

**Outcome:** a checklist of every feature actually working end-to-end, time-budgeted for the demo, with screenshots / fallbacks for any flakiness.

---

## 📋 6. Visuals & UX pass (Vista's surface)

Coordinate with Silas to spot-check Vista against:

- Are all key screens loading fast?
- Are the personas' flows visually distinct (owner dashboard vs applicant browse)?
- Empty states + error states polished?
- Mobile-responsive on demo screen?

Rubric: **Visuals & UX (5%)**.

---

## ✅ 7. Inspection notification + cancel gaps (found during Session 4 audit)

**SHIPPED** on branch `lukasio` (uncommitted). All four gaps closed:
- Gap A — `InspectionRequestedListener` now fans out to the active agent with a deterministic-but-distinct child eventId
- Gap B — `transitionFromPending` writes an `inspection.decided.v1` outbox event; new `InspectionDecidedListener` notifies the applicant; `NotificationKind.INSPECTION_APPROVED` and `INSPECTION_DECLINED` added
- Gap C — `InspectionService.cancelByEitherParty(callerId, requestId, reason)` plus `POST /api/inspections/{id}/cancel`; new `inspection.cancelled.v1` event + `InspectionCancelledListener` notifies the other parties; `V42__inspection_cancellation_reason.sql` adds the `cancellation_reason` column; new exceptions `InspectionCancellationReasonRequiredException` (400) and `InspectionRequestNotCancellableException` (409); legacy `cancel()` marked `@Deprecated`
- Gap D — rich `@Operation` annotations + `@ApiResponses` + `@ExampleObject` on reschedule, complete, no-show, cancel

Vista task queue updated: VTASK-003 (cancel-with-reason), VTASK-004 (action menu), VTASK-005 (notifications) all marked `READY FOR VISTA` with full contracts.

---

## (history) 7. Inspection notification + cancel gaps (found during Session 4 audit)

Three gaps discovered while walking through the inspection lifecycle code. All are "started but not finished" — comments mention the intended behaviour but the implementation isn't there.

**Gap A — agent doesn't get notified of new inspection requests.** `InspectionRequestedListener` only fans out to the owner, even though `InspectionService` has a comment about "async Kafka fanout to owner + agent". Fix: in the listener, look up `listingService.activeAgentUserId(listingId)` and notify the agent too if non-null. ~1 hour with tests.

**Gap B — applicant gets no notification when owner approves or declines.** `transitionFromPending()` just flips the status and saves; no outbox event, no sync notification. The applicant has to refresh the page to find out. Fix: write an outbox event in `transitionFromPending()` and add a listener that notifies the applicant. ~1-2 hours with tests.

**Gap C — no cancel path after APPROVED, and no cancellation reason captured.** Today only PENDING requests can be cancelled, and only by the applicant. Once approved, both parties are locked in — applicant emergency = forced no-show on their record; owner emergency = ghosts the meeting. Persona-audit Temi flagged the original lock-in but the fix was scoped to PENDING only.

Fix:
- Extend `cancel()` to allow APPROVED → CANCELLED for both applicant AND owner/agent
- Add a **required** `cancellationReason` field (max ~200 chars) — captured on the row + included in the notification to the other party so they understand what happened
- Update OpenAPI annotation + Vista doc to reflect the new shape
- ~2-3 hours with tests + state-machine update

**Gap D — no docs for the post-APPROVED action menu.** Once a request is APPROVED, the available actions (reschedule to a new slot, mark completed after the inspection, mark no-show if applicant didn't turn up, plus the cancel-with-reason from Gap C) aren't surfaced anywhere a frontend dev can discover them. The endpoints exist; nobody has documented "here's what an owner / agent can do on an APPROVED request and when". Fix:
- Add `@Operation` summaries + descriptions on the four endpoints in `InspectionController` (reschedule, complete, no-show, cancel)
- Create `docs/vista/inspection-post-approved-actions.md` mirroring the structure of `dream-ai-compare-frontend-prompt.md` — full request/response shapes, when each action is valid (status guard), error codes, UX recommendations (e.g. "show 'Mark completed' only after slot's end-time has passed")
- ~1-2 hours

All four feel like they belong in one PR — "inspection notifications + cancel completeness + post-APPROVED docs". Decent demo-day talking point ("we noticed during the audit that...").

---

## 📋 8. Comment threading (backend gap blocking Vista's Session-5 slice)

Vista wants threaded comments (replies under a parent). Today comments are flat — no `parent_comment_id` column, no entity field, no service support. This blocks "owner reply per comment id" and the general threading UI.

Fix:
- New Flyway migration adding `parent_comment_id BIGINT NULL REFERENCES comments(id)` to `comments`
- Add field to `Comment` entity
- Repository: `findByListingIdAndDeletedAtIsNullOrderByCreatedAtAsc()` already exists; add `findByParentCommentIdAndDeletedAtIsNullOrderByCreatedAtAsc()` or restructure to return parent + children in one query
- Service: accept optional `parentCommentId` in create flow; validate parent exists, is non-deleted, and belongs to the same listing
- OpenAPI annotations on `POST /listings/{id}/comments` to reflect the new optional field
- Persona-audit: check whether Silas/Vista wants flat-list-with-children or nested tree response shape (affects DTO design)
- ~3-4 hours with tests

## 📋 9. "Can I review?" pre-check endpoint (Vista wants this for the post-close CTA)

For Vista's "Review [owner/agent]" CTA to know whether to show on a closed listing, they need a way to ask the backend "is this user eligible to review the owner of this listing?" before they navigate to the review form.

Today the eligibility check happens server-side at POST time (returns 403 if not a deal participant). For UX, Vista needs an idempotent GET that returns `{eligible: true|false, reason?: string}` so the CTA can be conditionally rendered.

Fix:
- New endpoint: `GET /api/listings/{id}/reviews/me/eligibility` → returns `{canReviewOwner: bool, canReviewAgent: bool, reasons?: {...}}`
- Internally reuses the existing eligibility logic from `ReviewService.submit()`
- Auth: must be logged in (returns 200 even if not eligible — that's data, not an error)
- OpenAPI annotation
- ~1-2 hours with tests

## 📋 10. Surface the existing comment flag in OpenAPI + Vista docs

`CommentFlagService` + `CommentFlagController` already exist in the backend but Vista doesn't know about them. The "Flag comment under ⋯" feature is shippable today; just needs:
- Confirm the existing endpoint is reachable + tested
- Add `@Operation` annotation on the flag endpoint if missing
- Add a brief Vista integration note pointing at the endpoint
- ~30 min

## 📋 11. Allow reviewing the assigned agent (Session-5 audit finding)

Today `ReviewService.post()` enforces that the reviewee must be the listing owner (line 87: `if (!listing.ownerId().equals(revieweeId)) throw InvalidRevieweeException`). So agents who facilitate deals can't ever be reviewed — even when they did all the work.

For Emeka (and every agent) to build a public rating, applicants need to be able to review them too.

Fix:
- Extend the eligibility check: `revieweeId` is valid if it's the owner OR an agent with an ACCEPTED `agent_listings` row on the listing at deal time
- Owner-side review of agents would also be useful (owners rate agent performance) — same extension
- Aggregate calculation needs to handle agent reviews separately (so a user can have both "as owner" and "as agent" aggregates)
- Frontend (Vista) needs a chooser when a closed deal had an agent: "Review the owner / Review the agent / Both"
- ~3-4 hours backend with tests

## 📋 12. Enforce "at most one OPEN listing per (property, listing_type)"

Today `ListingService.create()` has zero guards against creating multiple OPEN listings on the same property. The migration comment in `V3` hints that the design intent was "simultaneous rent + sale" (one OPEN RENT + one OPEN SALE on the same property is intentional), but the code doesn't enforce that bound — you could create 50 OPEN RENT listings on one property and nothing complains.

Real-world impact: an owner accidentally creates a duplicate listing, two different applicants submit offers on two different listing IDs for the same physical property, both get accepted (each on its own listing), and one accepted offer is honoured while the other applicant shows up confused on move-in day.

Fix:
- Add a partial unique index: `CREATE UNIQUE INDEX listings_one_open_per_type_per_property ON listings (property_id, listing_type) WHERE status = 'OPEN'`
- Translate the unique-key violation to a 409 with a meaningful ProblemDetail (`listing.duplicate-open-listing-for-property-and-type`)
- Service-level pre-check + nicer error message before hitting the DB
- Tests: create OPEN RENT → create OPEN RENT same property → expect 409; create OPEN RENT → create OPEN SALE → both succeed
- Document the rule on the OpenAPI annotation for POST /listings
- ~1-2 hours with tests

## 🚨 14. Verify Vista is actually serving production traffic (demo-claims audit)

`curl https://vista.dreamhomes.today/` returns HTTP 404. `curl https://vista.dreamhomes.today/listings` returns HTTP 404. Haven backend at `https://haven.dreamhomes.today/actuator/health` returns 200.

The 404s came from a server (not a connection error), so DNS resolves and Vercel (or whatever's hosting Vista) is responding — but the home page and listings page both 404. Either:
- Vista isn't actually deployed (build broken, env vars missing on Vercel, no recent push)
- A routing rule is misconfigured
- The curl request is missing a header Vista's edge requires (Accept, User-Agent gates)

**Action (5 minutes):**
1. Open `https://vista.dreamhomes.today/` in a real browser right now
2. If it works → no fix needed; the curl failures were header-related (Vercel sometimes blocks no-UA requests). Note this and move on.
3. If it doesn't work → ping Silas IMMEDIATELY. We cannot demo without Vista.

**If broken (1-2 hours with Silas):**
- Check the Vercel deploy log for the latest build
- Check env vars on Vercel — does Vista know where Haven lives?
- Re-deploy from main
- Re-curl + manual browser test to confirm

**Why this is urgent:** Demo day depends on Vista. We've been talking about backend-side fixes for two days and nobody has verified the actual surface judges will see. **Verify today.**

---

## 🚨 15. Clarify production deploy target (Railway vs EKS) with Silas

`.github/workflows/deploy-eks.yml` exists and triggers on push to main — auto-deploys to AWS EKS. But the production URL `haven.dreamhomes.today` returns 200 from what's almost certainly Railway (matches the bundled-Postgres + Confluent config that's been in `application.yml` for weeks).

So one of the following is true and we need to know which:

- **Option A**: Railway is the active production. The EKS workflow runs but the resulting EKS deploy is unused (dead-letter). Either delete the EKS workflow OR document it as "AWS staging".
- **Option B**: We've migrated to AWS and the EKS workflow is the active prod. Then `haven.dreamhomes.today` is pointed at an ELB in front of EKS, not at Railway. The whole "Session 1 — AWS migration, Silas-led" framing is correct.
- **Option C**: Both run in parallel for the demo window. Confusing, but viable. Document this explicitly.

**Action (10 minutes with Silas):**
1. Ask: "where is `haven.dreamhomes.today` actually pointed today? Railway DNS or an AWS ELB / Route53?"
2. Ask: "is the EKS workflow active or dead-letter?"
3. Document the answer in `docs/demo-prep/01-overview.md` under "Deployment shape" — replace the `[TBD]` markers with the real answer
4. Update `15. CI/CD claim` in the script to match whichever is true

**Why this is urgent:** If a judge asks "walk me through your deploy story" and you give the wrong answer, you lose credibility for everything else. Get the truth from Silas now so the script is accurate.

---

## 📋 16. Listing trust signals — two-tier badge + warning (demo-claims audit, revised)

The demo script claims listings carry two distinct trust signals:

- **"Possible Scam" warning** — shown when the **owner** is NOT verified (`User.identityVerifiedAt IS NULL`)
- **"Verified" badge** — shown when the **property documents** ARE verified (`Property.documentsVerifiedAt IS NOT NULL`)

Three states a listing can be in:

| Owner identity verified? | Property docs verified? | UI signal |
|---|---|---|
| ❌ | (any) | ⚠️ "Possible Scam" warning chip |
| ✅ | ❌ | Nothing (baseline / neutral) |
| ✅ | ✅ | ✅ "Verified" badge |

### Backend gap (~30 min)

`PropertySummary.documentsVerifiedAt` is already embedded in the listing response — good, that drives the "Verified" badge directly.

`User.identityVerifiedAt` is NOT in the listing response today. The listing response only exposes `ownerId`. So Vista would need an N+1 fetch (one `/users/{ownerId}/profile` per listing card in the browse feed) to render the "Possible Scam" warning. That's exactly the Silas-persona N+1 anti-pattern.

**Fix:** add `ownerIdentityVerifiedAt: Instant?` to `ListingResponse` alongside the existing `ownerPublicBio`. Populate it from the `User.identityVerifiedAt` column via the listing service's projection. ~30 min with tests (the service already joins user/property data for the projection — just one more column).

### Vista changes (~1 hour)

Once the backend embeds it, Vista renders two distinct chips on every listing card AND on listing detail:

- `ownerIdentityVerifiedAt === null` → render warning chip: "⚠️ Possible Scam — owner identity not verified"
- `propertySummary.documentsVerifiedAt !== null` → render badge: "✓ Verified property"
- Both conditions can be true simultaneously if the owner is unverified but the property docs got verified separately — render both chips (rare but valid)

**Copy suggestions:**
- Warning: "⚠️ Possible Scam — this owner hasn't completed identity verification. Be cautious before sending money or signing."
- Badge: "✓ Verified — property documents have been confirmed by our admins."

### Test plan

- Browse feed: an unverified-owner listing shows the warning chip; a verified-owner listing doesn't
- Same listing with verified property docs also shows the green badge
- Verify the seed has examples of both states (Amaka's listings = verified; need at least one unverified-owner listing for the warning state — could spin up a quick "Owner-Without-Verification" seed user)

**Total time: ~1.5 hours (backend 30min + Vista 1hr).** This is high-value — matches the script verbatim and is one of the most visually obvious trust signals in the product.

### OpenAPI / API docs requirements (explicit for UI clarity)

When the backend half ships, the `ListingResponse` schema annotations MUST spell out the trust-signal semantics so Vista (and any future integrator) doesn't have to reverse-engineer the rules. Add to the `@Schema` description on the relevant fields:

```java
@Schema(
    description = """
        Owner's identity-verification timestamp. **Null = owner has not completed
        identity verification.** UI should render a "⚠️ Possible Scam" warning chip
        on listings where this is null. Non-null = owner is verified; render no
        special owner-side signal (the absence-of-warning IS the signal).
        """,
    example = "2026-04-12T10:00:00Z",
    nullable = true)
private Instant ownerIdentityVerifiedAt;
```

And on `PropertySummary.documentsVerifiedAt`:

```java
@Schema(
    description = """
        Property-document verification timestamp. **Non-null = an admin has
        approved the property's title/registry documents.** UI should render a
        "✓ Verified" green badge on listings where this is non-null. Null means
        baseline (no badge); ownership of the property itself is not vouched for.
        """,
    example = "2026-04-12T10:00:00Z",
    nullable = true)
Instant documentsVerifiedAt;
```

Also extend the listing-detail `@Operation` description (in `ListingController`) with a short "Trust signal rendering" section that summarises the 3-state matrix above. This is the kind of inline OpenAPI documentation Silas asked for in Story 1 of his persona — the spec should answer "what does this mean for the UI?" without a Slack message.

**Vista integration doc:** create `docs/vista/listing-trust-signals.md` mirroring the existing `dream-ai-compare-frontend-prompt.md`. Include the 3-state matrix, the exact field names, recommended chip placement (card view + detail view), and copy.

---

## 📋 17. Add verification status to Dream AI catalogue (demo-claims audit)

Dream AI does natural-language search via embeddings + Claude Haiku ranking. The script demos a query like "2-bedroom in Surulere under 2 million naira **with verified owners**". The first three constraints (bedrooms, location, price) are in the catalogue passed to Claude. **The verification status is NOT in the catalogue**, so Claude can't filter on it.

Either drop "with verified owners" from the on-stage prompt OR ship this fix:

**Backend changes (~1 hour):**

1. `DreamAiService.buildListingsArrayJson()` — for each listing, add fields:
   - `ownerVerified` (bool) — derived from `User.identityVerifiedAt != null` for the owner
   - `propertyDocumentsVerified` (bool) — derived from `Property.documentsVerifiedAt != null`
   - Pull these from the existing `ListingWithProperty` projection if available, or extend it

2. `AnthropicListingSearchClient.SYSTEM` — extend the system prompt:
   ```
   - "ownerVerified" / "propertyDocumentsVerified" — when the user asks for "verified"
     owners or properties, prefer rows where the relevant field is true.
   ```

3. Test: a prompt that includes "verified" returns only verified-owner listings ranked first

**Frontend (Vista) changes:**

- None — the response shape stays `{listingIds: [...]}`. Just better filtered.

**Why bother:** the demo script explicitly demos this query. Without the fix, the model will return listings that ignore "verified" — embarrassing if judges look at the results.

---

## 📋 18. Confirm Temi + persona Bruno collection coverage (demo-claims audit)

Two sub-items.

**18a — Confirm Temi exists in `DemoDataSeeder` (~5 min):**

The seed has Amaka, Biodun, Emeka, Ngozi, Dayo, Adaeze, Babatunde + admin. Need to verify Temi is also seeded — script says "we can demo as Ngozi, Adaeze, Babatunde, Emeka, Amaka, Dayo" and earlier sessions reference Temi as a key persona. Open `DemoDataSeeder.java`, grep for "Temi" / "temi". If missing:

- Add `User temi = saveUser("temi.adekunle@demo.dreamhomes.local", Role.APPLICANT, "Temi Adekunle", "Temi", now)` (or whatever surname fits Temi's persona)
- She gets the same `Demo2026!` password as the other seeded accounts
- Verify her account works post-seed by logging in via the Ngozi Bruno collection swapped to her email

**18b — Spin up dedicated Bruno collections for Temi, Adaeze, Babatunde (~30 min each):**

Today `audit/bruno/` has folders for Amaka, Biodun, Dayo, Emeka, Ngozi. Adaeze + Babatunde appear *inside* the Demo orchestration but don't have standalone collections. Temi has nothing.

For each missing persona:
1. Copy `audit/bruno/Ngozi/` (closest archetype for applicants)
2. Swap login email + display name
3. Verify Bruno runs end-to-end (`bru run --env demo`)
4. Adjust the scenarios — Temi should do a fresh inspection booking (script says her inspection fires live on stage); Adaeze + Babatunde already have established histories per the Demo collection

**Why:** the script claim is "persona-driven test simulations" plural. Three personas have no standalone scripts. Either ship them or soften the claim to "5 production personas + Demo orchestration for the rest."

---

## 📋 19. Mock the liveness check endpoint with explicit v2 framing (demo-claims audit, revised)

The script claim about a liveness check is mockable for v1 — ship a stub endpoint with a clear `MOCKED` comment, document it in OpenAPI as a mocked surface, and frame it on stage as "the integration point is live; real biometric liveness via a third-party KYC provider is phase 2".

This is a better outcome than dropping the claim — it shows we've thought about the KYC integration boundary, and judges can see exactly where the real provider will plug in.

### Backend changes (~1 hour)

1. New endpoint: `POST /api/verifications/liveness-check` — authenticated, accepts optional body (a placeholder for "session ID" / "challenge response" that a real provider would consume)
2. Returns:
   ```json
   {
     "status": "PASSED",
     "score": 0.97,
     "checkedAt": "2026-05-24T08:30:00Z",
     "_mocked": true
   }
   ```
3. Service method `LivenessCheckService.runMockedCheck(userId)` returns the above fixed shape. Add a `// MOCKED — v2 integrates Smile ID / Dojah / Sourcefin` comment block at the top of the method.
4. Persist a `liveness_check_results` row (new Flyway migration) so the verification submit endpoint can reference it (`livenessCheckId`)
5. `POST /api/verifications` already accepts `documentRefs`; extend the request DTO to accept an optional `livenessCheckId` — when present, the submit endpoint validates it belongs to the caller and is still PASSED
6. Tests: happy path + missing liveness ref + foreign liveness ref (other user's)

### OpenAPI annotations (explicit for UI clarity)

Critical — the spec MUST flag this as mocked so frontend integrators and judges know exactly what they're looking at:

```java
@Operation(
    summary = "(MOCKED) Run a liveness check before verification submission",
    description = """
        **⚠️ This endpoint is MOCKED for v1.** It always returns a PASSED result
        with score 0.97 regardless of input. The integration point is in place
        so v2 can swap in a real biometric provider (Smile ID, Dojah, Sourcefin)
        without changing the caller contract.

        **What v2 will do:** open a camera session, ask the user to blink /
        turn head / smile on command, verify the motion is real-time (not a
        recording), and return PASSED/FAILED with a confidence score.

        **What v1 does:** returns PASSED. The {@code _mocked: true} flag in
        the response makes it obvious this isn't real.

        Callers should still consume + persist the response; verification
        submit requires a {@code livenessCheckId} from a PASSED result.
        """
)
```

Similarly on the verification submit endpoint's `livenessCheckId` field — mark it explicitly: "(MOCKED v1) Reference to a passed liveness check. v2 will require this to come from a real provider; v1 accepts any caller's own mocked PASSED row."

### Frontend (Vista) integration

- The verification flow gains a "verify liveness" step before document upload
- Shows a placeholder UI: "[Camera box] We'd ask you to blink and turn your head here — this is mocked for v1, we'd swap in Smile ID's SDK for production"
- Tap "Run mocked check" → calls the endpoint → stores the returned `livenessCheckId` → passes it to the verification submit
- On-stage demo: walk through the flow, mention the mock framing once, move on

### Script wording for May 26

> *"For identity verification, we built the full document-upload + admin-review flow you'd expect, plus a liveness-check integration point that's currently mocked. The mock returns a fixed PASSED result so the flow works end-to-end; v2 plugs in a real KYC provider like Smile ID or Dojah behind the same endpoint. Both backend and the OpenAPI spec mark it explicitly as mocked — no surprises for our integrators."*

This is honest, shows engineering judgment, and gives judges something tangible to look at.

---

## 📋 20. Automated verification process — mocked, swappable providers (demo-claims audit follow-up)

Today every verification submission goes into the admin queue and a human (Dayo) decides approve/reject by eyeballing the uploaded document. That's manual + slow. The script's framing of "automated verification" needs a **first-pass automated check** that runs before the admin even sees the row — surfacing OCR'd fields, identity matches, license lookups, and document authenticity scores.

For v1 this is **mocked**. The shape, the integration boundary, and the swap-the-provider design need to be real so v2 is a config + implementation swap, not a refactor.

This task is the broader sibling of Item 19 (liveness mock). Item 19 covers ONE specific check (liveness). Item 20 covers the **whole automated verification pipeline** with a pluggable provider abstraction.

### Provider abstraction (Strategy pattern)

```java
package com.dreamhomes.haven.verification.automation;

public interface VerificationProvider {
    /** Provider name for logging + audit. e.g. "MOCK", "SMILE_ID", "DOJAH", "SOURCEFIN". */
    String name();

    AutomatedCheckResult verifyOwnerIdentity(OwnerIdentityCheckRequest req);
    AutomatedCheckResult verifyAgentCredentials(AgentCredentialsCheckRequest req);
    AutomatedCheckResult verifyApplicantIdentity(ApplicantIdentityCheckRequest req);
    AutomatedCheckResult verifyPropertyDocuments(PropertyDocumentCheckRequest req);
}
```

`AutomatedCheckResult` is a record with: `status` (PASSED / FAILED / NEEDS_HUMAN_REVIEW), `score` (0.0–1.0 confidence), `extractedFields` (Map<String, Object> of OCR'd / parsed fields like name, DOB, license number), `providerReference` (provider's own ID for audit), `rawResponse` (JSON blob from the provider for debugging), `runAt` (Instant).

### Three implementations to scaffold (only Mock is real for v1)

**`MockVerificationProvider`** (ACTIVE for v1):
- Returns PASSED with score=0.95 and plausible mock-extracted fields ("NIN: 12345678901", "Name match: 0.98")
- Comment block: `// MOCKED v1 — all checks PASS. Production providers below.`
- Spring `@Component` + `@ConditionalOnProperty(name = "haven.verification.provider", havingValue = "mock", matchIfMissing = true)`

**`SmileIdVerificationProvider`** (SCAFFOLDED for v2):
- Empty class with method stubs
- Each method body: `throw new UnsupportedOperationException("TODO: integrate Smile ID — see https://docs.smileidentity.com/...")`
- Class-level Javadoc lists the Smile ID endpoints each method would call: e.g. `/v1/id_verification` for owner identity, `/v1/business_verification` for agent credentials
- `@ConditionalOnProperty(name = "haven.verification.provider", havingValue = "smile-id")`

**`DojahVerificationProvider`** (SCAFFOLDED for v2, alternative):
- Same shape as Smile ID — empty stubs, Javadoc pointing at Dojah's API docs
- Demonstrates that swapping providers = swap one config value

To switch providers at deploy time: `HAVEN_VERIFICATION_PROVIDER=smile-id` env var. No code changes to anything that depends on `VerificationProvider`.

### Data model — `verification_automation_results` table

New Flyway migration (`V42__verification_automation_results.sql`):

```sql
CREATE TABLE verification_automation_results (
    id                   BIGSERIAL    PRIMARY KEY,
    verification_id      BIGINT       NOT NULL REFERENCES verifications(id),
    check_type           VARCHAR(64)  NOT NULL,
    -- check_type values: OWNER_IDENTITY, AGENT_CREDENTIALS, APPLICANT_IDENTITY,
    -- PROPERTY_DOCUMENTS, LIVENESS (links to Item 19's table or replaces it)
    provider_name        VARCHAR(64)  NOT NULL,   -- MOCK, SMILE_ID, DOJAH, ...
    status               VARCHAR(32)  NOT NULL,   -- PASSED, FAILED, NEEDS_HUMAN_REVIEW
    score                NUMERIC(4,3),            -- 0.000 - 1.000
    extracted_fields     JSONB,                   -- {nin: "...", nameMatch: 0.98}
    provider_reference   VARCHAR(255),            -- the provider's own ID
    raw_response         JSONB,                   -- provider's full response (debugging)
    run_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT verification_automation_status_check
        CHECK (status IN ('PASSED', 'FAILED', 'NEEDS_HUMAN_REVIEW'))
);

CREATE INDEX verification_automation_results_verification_idx
    ON verification_automation_results (verification_id);
```

Multiple checks per verification (e.g. an owner submission runs liveness + identity-doc + NIN match = 3 rows).

### Integration into the existing verification flow

When a user submits a verification:

1. Existing flow: validate request, persist `Verification` row as PENDING
2. **New step**: `AutomatedVerificationService.runChecksFor(verification)` runs the appropriate checks for the verification type:
   - OWNER_IDENTITY → liveness + identity-doc + (optional) NIN lookup
   - AGENT_CREDENTIALS → liveness + identity-doc + license lookup
   - APPLICANT_IDENTITY → liveness + identity-doc
   - PROPERTY_DOCUMENTS → document-authenticity check + (optional) lands registry lookup
3. Each check writes a row to `verification_automation_results`
4. If ALL checks PASS with score > 0.85 → auto-approve the verification (stamp `identityVerifiedAt` / `documentsVerifiedAt`) without admin involvement
5. If any check FAILS → flag the verification, admin still reviews
6. If any check NEEDS_HUMAN_REVIEW → flag for admin (default for v1 mock — even mocked checks should sometimes route to humans so the admin queue isn't empty in the demo)

The admin UI shows the automated check results alongside the document so Dayo sees "Mock provider says PASSED 0.95, here's the extracted NIN — does it match the document?" and can approve in one click.

### Service layer

```java
@Service
@RequiredArgsConstructor
public class AutomatedVerificationService {
    private final VerificationProvider provider;  // Spring injects whichever is active
    private final VerificationAutomationResultRepository resultRepo;
    private final VerificationRepository verificationRepo;

    @Transactional
    public List<AutomatedCheckResult> runChecksFor(Verification v) {
        // dispatches to the right provider method based on v.type
        // persists each result
        // returns the list for the calling submit service to inspect
    }
}
```

### OpenAPI annotations (explicit for UI clarity)

The verification submit endpoint's response should include an `automatedChecks` array so Vista (and admins) can see what ran:

```java
@Schema(
    description = """
        Results of the automated verification checks run when this verification
        was submitted. **In v1 these are MOCKED** (provider = "MOCK", all PASS).
        In v2, replace the provider via {@code HAVEN_VERIFICATION_PROVIDER}
        env var — supported: smile-id, dojah. Each check's {@code providerName}
        field tells the caller which provider produced it.
        """)
List<AutomatedCheckResultResponse> automatedChecks;
```

Add the same explicit mocked-v1 framing on the admin queue response — admins should see "Mock provider says PASSED" so they understand they're still the source of truth in v1.

### TODO comments at the integration boundary

Inside `SmileIdVerificationProvider` and `DojahVerificationProvider`, every method body should contain:

```java
// TODO: v2 — integrate Smile ID
// 1. Acquire credentials from secrets (see SecretsConfig)
// 2. Construct request: POST https://api.smileidentity.com/v1/id_verification
//    Body: { partner_id, signature, user_id, id_type: "NIN", id_number, ... }
// 3. Parse response into AutomatedCheckResult — see Smile ID's response schema
// 4. Handle rate limits + retries — Smile ID returns 429 above 60 req/min on the trial tier
// 5. Map their status codes to ours (PASSED / FAILED / NEEDS_HUMAN_REVIEW)
throw new UnsupportedOperationException(
    "TODO: integrate Smile ID provider — see https://docs.smileidentity.com/products/biometric-kyc"
);
```

This makes the integration boundary unambiguous — anyone picking up the v2 work knows exactly which API calls to make and which fields to map.

### Tests (TDD-first per CLAUDE.md)

- Unit test: `MockVerificationProvider.verifyOwnerIdentity()` returns PASSED with non-empty extracted fields
- Service test: `AutomatedVerificationService.runChecksFor(ownerVerification)` runs the right checks and persists rows
- Integration test: full submit flow with mock provider auto-approves verifications that all-PASS
- Provider-swap test: when `HAVEN_VERIFICATION_PROVIDER=smile-id`, attempting a check throws `UnsupportedOperationException` (proves the swap mechanism works even though Smile ID isn't implemented)

### Time estimate

- Interface + provider abstractions + Mock impl: ~2 hours
- Migration + repo + service: ~1 hour
- Integration into submit flow + admin queue: ~1 hour
- OpenAPI annotations + TODO blocks: ~30 min
- Smile ID + Dojah scaffolded stubs (no impl, just TODO blocks): ~30 min
- Tests: ~1.5 hours

**Total: ~6 hours.** Larger than most items here, but it's the most "we thought about productionising this" story for the demo + a clean v2 starting point.

### Script wording for May 26

> *"For verification we built a fully abstracted automated-check pipeline. In v1 the provider is mocked — every check returns PASSED with a confidence score so the flow works end-to-end. In v2 we swap one env var to point at Smile ID, Dojah, or Sourcefin without touching any business logic. The mock provider is annotated in OpenAPI as MOCKED so frontend integrators and judges aren't confused. The provider abstraction means we can also A/B test providers or fail over between them — meaningful for a Nigerian product where any single KYC provider has occasional outages."*

---

## 📋 21. Expose verification `decisionReason` back to the submitter (Session-7 audit finding)

When an admin rejects a verification, they're required to supply a reason (`VerificationAdminService.reject()` throws if missing). The reason is persisted on the `verifications.decision_reason` column. **But the submitter never sees it.**

`VerificationResponse.java` deliberately omits the field — the Javadoc says: *"We don't expose `decisionReason` on submission responses."* So the user who got rejected sees only a REJECTED status + a `decidedAt` timestamp. They have to guess what to fix.

Real impact: Amaka submits her identity → admin rejects with "photo too blurry, retake in better light" → Amaka's dashboard shows REJECTED → she resubmits with the same blurry photo → admin rejects again → cycle repeats. Persona-audit catch — every rejected user is stuck in a guessing loop.

### Fix

- Add `decisionReason: String?` to `VerificationResponse`
- Only populate it when status is REJECTED (no point sending null on PENDING/APPROVED)
- Update OpenAPI annotation to make the field's purpose explicit: "Only present when status is REJECTED. The reason supplied by the admin who rejected this verification — show this to the user so they know what to fix on resubmit."
- Update `VerificationMapper` / wherever the response is built
- Update existing tests + add a new test asserting the field is populated on rejected rows and null on PENDING/APPROVED
- ~30 minutes total

### Frontend (Vista)

- On the verification dashboard, render the reason prominently when status=REJECTED — copy: "Your verification was rejected: \"{decisionReason}\". Address this and resubmit below."
- Trivially small change once backend ships

### Why was it deliberately hidden?

The Javadoc doesn't justify the choice. Best guess: defensive over-caution about admins writing things they shouldn't ("internal moderation note: this guy looks dodgy"). But that's a content-discipline problem, not an API design problem — solve it with admin training, not by withholding actionable feedback.

If we genuinely want admin-private notes vs user-facing reasons, the right answer is two columns: `decisionReason` (user-facing) + `internalAdminNote` (admin-only). But for v1, just exposing the one column is the right move.

---

## 📋 22. Dream AI embedding-distance threshold (Session-9 cost-defence)

Today the pgvector NN query always returns the top-K nearest listings regardless of how poor the match is. So a junk query like "purple elephant tap dance" still gets 80 listings sent to Claude and costs $0.02. There's no early bail-out for irrelevant queries.

### The fix

Add a cosine-distance cutoff to the embedding NN query:

```sql
SELECT id FROM listings
WHERE status = 'LIVE'
  AND embedding <=> :query < :max_distance   -- NEW
ORDER BY embedding <=> :query
LIMIT :limit
```

If zero candidates clear the threshold → return the `kind=no_results` outcome directly, skipping the Claude call entirely.

### Three approaches in increasing sophistication

- **Absolute cap** — single threshold like `0.5`. Easiest to ship, easiest to revert.
- **Relative gap to best match** — include everything within +20% of the best-match distance, plus an absolute backstop. Handles query variance well.
- **Hybrid (production-grade)** — absolute hard cap + relative percentile cut within that. Best quality vs cost.

Recommend starting with absolute cap, instrumenting the distance distribution, then evolving.

### Tuning is empirical

You can't pick the threshold from theory — measure first:

1. Sample 50 real + adversarial prompts; log their nearest-K distances
2. Plot the distribution; eyeball where "good" matches end and "noise" begins
3. Pick that as the initial threshold
4. Re-tune as the corpus grows (more listings → denser meaning-space → different distance norms)

### Implementation

- Add `haven.dream-ai.embedding.max-distance: 0.5` (env-overridable) to `application.yml`
- Update `ListingSearchEmbeddingStore.nearestLive` signature: `nearestLive(query, limit, maxDistance)`
- `DreamAiService.suggestWithAnthropicOutcome`: if returned list is empty AND embeddings are active → return `DreamAiSuggestOutcome.empty(false, true)` (queryTooStrict=true) without calling Claude
- Tests: junk prompt → no Claude call, returns empty outcome; specific prompt → Claude called normally; threshold-edge prompt → catalog smaller than max

### Cost impact

- Junk queries (~10-20% of public traffic): full save (~$0.02 each)
- Vague queries: ~60% save (smaller catalog → fewer tokens)
- Specific queries: no change

Net: probably **20-30% Anthropic spend reduction** on typical traffic, more under adversarial traffic where attackers are intentionally sending nonsense.

### Time

- Backend + tests: ~2 hours
- Instrumentation for tuning: ~30 min (log distance distribution to Prometheus)
- Empirical tuning + threshold finalization: ~30 min once data is collected

---

## 📋 23. Make the Claude ranking step optional (Session-9 cost-defence)

Today the Anthropic call is unconditional whenever `HAVEN_ANTHROPIC_API_KEY` is set and the embedding index is populated. Every query costs ~$0.02 even when the embedding NN order is already good enough for the user's intent.

### Goal

Add a way to bypass the Claude call and return the pgvector NN order directly. Saves the per-query LLM cost when the lift from LLM reasoning is small.

### Three approaches in order of complexity

- **A — Per-request toggle**: query param `?rankMode=fast` (embeddings-only) vs `?rankMode=smart` (Claude). Default stays smart. Cleanest, easiest to implement, lets the client decide. Vista could expose a "quick search" vs "smart search" toggle.
- **B — Automatic by query shape**: a heuristic checks the prompt for constraint indicators ("under ₦", "verified", "more than X bedrooms") and only invokes Claude when constraints are detected. Hard to tune, brittle.
- **C — Tier-based**: anonymous → embeddings-only (free path); authenticated → Claude (smart path). Aligns cost with value, also lowers cost-drain attack surface from anonymous abuse.

Recommend shipping **A + C combined**: per-request toggle defaulting to fast for anonymous, smart for authenticated, frontend can override.

### Implementation

- New enum `DreamAiRankMode { FAST, SMART }` (or use the existing settings if there are any)
- `DreamAiRunTurnRequest` gains optional `rankMode` field
- `DreamAiService.suggestWithAnthropicOutcome` branches:
  - `FAST` → skip Claude, just trim the candidate list to `MAX_RESULTS` (20) and return
  - `SMART` → existing behaviour
- Anonymous orchestrator path defaults to FAST when no rankMode provided
- Authenticated orchestrator path defaults to SMART
- Always allow client override (a logged-in user can request `rankMode=fast` to save Anthropic budget on a low-stakes query; an anon user with a real query can request `rankMode=smart` if we want to allow that — probably gate it on a captcha)

### Trade-off honest

Embeddings-only ranking is decent for general queries like "3-bed flat in Lekki" — the embedding captures vibe and ranks similarly-vibed listings well. It's WEAK on constraint-heavy queries like "under ₦4m AND verified AND pets". The embedding might rank a ₦5m listing higher than a ₦3.5m one if the vibe matches; Claude reads the JSON and actually reasons about constraints.

So the toggle is "cheap and fast vs slow and smart". Not free, has UX implications. Worth measuring which mode users actually prefer.

### Tests

- `rankMode=fast` → no Anthropic call made (mock verifies); returned ids are in pgvector distance order (verifiable against the candidate list)
- `rankMode=smart` → Anthropic called, returned ids in Claude's chosen order
- Anonymous + no rankMode → defaults to fast (verifies the auto-default rule)
- Authenticated + no rankMode → defaults to smart

### Cost impact

If, say, 60% of traffic routes through FAST (because anonymous + simple queries dominate):

- Today: 100% × $0.02 = $0.02/query average
- After: 40% × $0.02 = $0.008/query average — **~60% reduction**

Pairs nicely with Item 22 (distance threshold). Combined effect: ~70-80% of the original Anthropic spend cut, with minimal quality loss on the queries that get downgraded.

### Time

- Backend toggle + branching + tests: ~2 hours
- Vista UI toggle: ~30 min (or just defer to the default behaviour and let the rank-mode be invisible to most users)

---

## 📋 24. Wire `HAVEN_OPENAI_API_KEY` into application.yml (Session-9 audit finding — real bug)

**`ListingEmbeddingProperties.java` reads from `haven.dream-ai.embeddings.openai-api-key` but `application.yml` has NO `embeddings:` section.** So even if `HAVEN_OPENAI_API_KEY` is set as a Railway env var, it never reaches the config property. `ListingEmbeddingProperties.active()` always returns false. The entire pgvector NN candidate selection is dead code.

Verification: grep `application.yml` for "embeddings" — zero matches. The Java class expects:
- `haven.dream-ai.embeddings.openai-api-key`
- `haven.dream-ai.embeddings.model`
- `haven.dream-ai.embeddings.dimensions`
- `haven.dream-ai.embeddings.openai-base-url`
- `haven.dream-ai.embeddings.connect-timeout-ms`
- `haven.dream-ai.embeddings.read-timeout-ms`

None of these are bound to env vars in YAML.

### Fix

Add to `application.yml` under `haven.dream-ai:`:

```yaml
    embeddings:
      # When set, candidate selection uses OpenAI text-embedding-3-small + pgvector NN.
      # Leave empty to use first-page catalogue (legacy fallback).
      openai-api-key: ${HAVEN_OPENAI_API_KEY:}
      model: ${HAVEN_OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
      dimensions: ${HAVEN_OPENAI_EMBEDDING_DIMENSIONS:1536}
      openai-base-url: ${HAVEN_OPENAI_BASE_URL:https://api.openai.com}
      connect-timeout-ms: ${HAVEN_OPENAI_CONNECT_TIMEOUT_MS:10000}
      read-timeout-ms: ${HAVEN_OPENAI_READ_TIMEOUT_MS:60000}
```

### Backfill needed once enabled

When OpenAI is wired and a key is set, existing LIVE listings have no embedding rows (the seeder doesn't trigger refresh, and live edits since launch have been few). Need a one-time backfill script:

- New endpoint or boot-time job: iterate all LIVE listings, call `listingSearchEmbeddingService.scheduleRefreshListing(id)` for each
- Wait for completion (background work)
- Confirm `listing_search_embeddings` row count matches LIVE listing count

### Time

- YAML addition + tests: ~15 minutes
- Backfill script + run: ~30 minutes
- Verify Dream AI now picks candidates by semantic similarity instead of browse order: another 15 minutes

Total: ~1 hour. Real impact — unlocks all the semantic search this project paid embedding-infrastructure cost for.

---

## 📋 25. Refactor Dream AI providers into swappable Service → Provider pattern (Session-9 follow-up)

Today the Anthropic and OpenAI integrations are baked directly into specific client classes (`AnthropicListingSearchClient`, `AnthropicListingCompareClient`, `OpenAiEmbeddingsClient`). Swapping providers (e.g. Anthropic → Gemini, OpenAI → Voyage) means rewriting these classes. Mirror the Item 20 verification-provider design so both AI surfaces are config-swappable.

### Two new provider interfaces

```java
package com.dreamhomes.haven.dreamai.provider;

public interface LlmRankingProvider {
    /** Provider name for logging + meta.provider field. e.g. "anthropic", "openai", "gemini". */
    String name();

    /** Rank candidate listing ids best-to-worst given the user query. */
    List<Long> rankListingIds(String userQuery, String catalogJson, Set<Long> validIds);

    /** Structured compare across 2-5 listings — pros/cons + recommendation. */
    CompareReasoning compareListings(String userIntent, String catalogJson, Set<Long> validIds);
}

public interface EmbeddingProvider {
    String name();

    /** Embed arbitrary text into a vector. Returns null/empty if provider is dark. */
    float[] embed(String text);
}
```

### Implementations to ship

**`LlmRankingProvider`**:
- `AnthropicLlmRankingProvider` — wraps the existing two Anthropic clients (just a thin facade over `AnthropicListingSearchClient` + `AnthropicListingCompareClient`). ACTIVE for v1.
- `OpenAiLlmRankingProvider` — SCAFFOLDED with `// TODO: integrate OpenAI chat completion with structured JSON output`
- `GeminiLlmRankingProvider` — SCAFFOLDED with `// TODO: integrate Google Gemini`

**`EmbeddingProvider`**:
- `OpenAiEmbeddingProvider` — wraps the existing `OpenAiEmbeddingsClient`. ACTIVE for v1.
- `VoyageEmbeddingProvider` — SCAFFOLDED with `// TODO: integrate Voyage AI (Anthropic-recommended) — see https://docs.voyageai.com/`
- `SelfHostedEmbeddingProvider` — SCAFFOLDED with TODO pointing at Hugging Face TEI or sentence-transformers + the deploy pattern

### Service layer

`DreamAiService` no longer talks to specific clients. It talks to:

```java
@Service
@RequiredArgsConstructor
public class DreamAiService {
    private final LlmRankingProvider llmProvider;       // Spring injects whichever is active
    private final EmbeddingProvider embeddingProvider;
    private final ListingService listingService;
    // ...
}
```

The provider beans use `@ConditionalOnProperty` to pick exactly one at boot:

```java
@Component
@ConditionalOnProperty(name = "haven.dream-ai.llm-provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicLlmRankingProvider implements LlmRankingProvider { ... }
```

### Selection via env var

```bash
HAVEN_DREAM_AI_LLM_PROVIDER=anthropic    # default
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=openai # default
```

Switch to Voyage embeddings + Gemini ranking:

```bash
HAVEN_DREAM_AI_LLM_PROVIDER=gemini
HAVEN_DREAM_AI_EMBEDDING_PROVIDER=voyage
```

No code changes anywhere downstream of the providers. The orchestrator, the chat service, the controller — all unchanged.

### Config refactor

`DreamAiAnthropicProperties` stays for Anthropic-specific knobs (model, base URL, timeouts). New `DreamAiOpenAiLlmProperties`, `DreamAiGeminiProperties`, `DreamAiVoyageProperties` for each provider's specifics. All optional — only the active provider's properties need to be valid.

### Stub-mode framing

Keep the existing "no LLM provider configured" fallback to substring stub. Just rephrase: if neither LLM nor embedding provider is active → stub mode. If embedding active but LLM dark → embedding-only ranking (Item 23). If LLM active but embedding dark → browse + LLM (current behaviour).

### OpenAPI / docs

- The `meta.provider` field in turn responses already exists — extend it to include both `llmProvider` and `embeddingProvider` so the UI / debug tools can see what ran
- New OpenAPI annotation on the suggest endpoint explaining the bimodal provider config
- New `docs/dream-ai-providers.md` listing the three LLM + three embedding options, when to pick each, what to set on Railway

### TDD-first

- Service test: `DreamAiService.suggest()` calls `llmProvider.rankListingIds()` via mock — provider-agnostic
- Provider unit tests: each concrete provider has its own test (with its own HTTP mock); mock provider for the swap-mechanism test
- Integration test: `HAVEN_DREAM_AI_LLM_PROVIDER=openai` attempts a call → throws UnsupportedOperationException from the scaffolded stub → proves the swap mechanism works even before OpenAI implementation lands

### Why this matters beyond cleanliness

1. **Negotiating leverage** — when Anthropic raises prices or Voyage offers a deal, switch in 5 minutes via env var, not a multi-day refactor
2. **Outage resilience** — if Anthropic has a regional outage, flip to Gemini until it's resolved (currently we'd be hard-down on Dream AI)
3. **A/B testing** — can run two providers in parallel and compare quality / cost
4. **Honest demo answer** — "we treat both LLM and embedding providers as swappable infrastructure, not hardcoded dependencies"

### Time

- Provider interfaces + `AnthropicLlmRankingProvider` + `OpenAiEmbeddingProvider` (the two active wrappers): ~2 hours
- Scaffolded stubs for alternatives (Voyage, Gemini, OpenAI LLM, SelfHosted): ~1.5 hours total
- Service refactor + config-property wiring: ~1 hour
- Tests + provider-swap integration test: ~1.5 hours
- OpenAPI + `docs/dream-ai-providers.md`: ~45 min

**Total: ~6-7 hours.** Same shape as Item 20, just for the AI plumbing instead of the KYC plumbing.

---

## 📋 26. Dream AI orchestrator UX overhaul (Session-9 finding)

The orchestrator currently routes prompts via regex + length checks, which leads to bad UX. Several specific issues:

**Sub-task A — Adaptive clarify chips (~2 hours)**

Today: three hardcoded chips ("Budget", "Area", "Rent or Buy") shown regardless of what the user typed. So typing "lekki" still shows an "Area" chip — embarrassing.

Fix:
- Parse the user's prompt for already-provided constraints (regex for area names, "X bedroom", "under ₦Y", "rent"/"sale")
- Only show chips for the constraints NOT already provided
- If user typed "lekki" → chips: Budget + Bedrooms + Rent/Buy (Area dropped because they said it)
- If user typed "3 bed under ₦4m" → chips: Area + Rent/Buy

**Sub-task B — Replace URL-pasted compare with selection state (~3 hours, mostly Vista)**

Today: to compare, users must paste `/listings/17` URLs into chat. Nobody does this.

Fix:
- Vista shows a checkbox on each listing card
- After user checks 2-5 listings, a "Compare selected" button appears
- On tap, Vista posts `{prompt: "compare these for me", compareListingIds: [17, 42, 89]}` to the suggestions endpoint
- Backend gains a `compareListingIds` field on `DreamAiRunTurnRequest` — when present, route straight to compare path (skip URL extraction entirely)
- Existing URL-paste compare still works for backwards compat

**Sub-task C — Soft fallback on no_results (~1 hour)**

Today: "Some listings were considered but none ranked high enough — relax budget, area, or filters." User has to guess what to relax.

Fix:
- When Claude returns empty, run a secondary lookup with relaxed constraints (e.g. drop the price filter and re-rank top 3)
- Return: "Nothing matched under ₦4m. Here are 3 options at ₦4.2–4.8m — would these work?"
- Frontend renders the suggestion + a one-tap "Yes, show these" button

**Sub-task D — Intent classification (LARGER, future work)**

Replace the regex routing with a lightweight LLM-based intent classifier:
- Input: user prompt + minimal chat history
- Output: intent enum (SEARCH | COMPARE_RECENT | CLARIFY | INFO_QUERY | SAFETY_FLAG | etc.)
- Routes based on intent, not regex

Cost: one tiny additional Claude call (~$0.001 per query because the prompt is short and structured output is small). Could even use Anthropic's lower tier (Sonnet → Haiku → off-the-shelf classifier).

Time: ~4-6 hours. Real product work. Probably post-demo.

**Sub-task E — Mode honesty indicator (~30 min Vista)**

When `meta.provider === "stub"`, Vista renders a subtle indicator: "Quick search (smart search unavailable)". Doesn't lie to the user about which mode they're in.

### Demo-day priorities

For May 26:
- **B (UI-level compare)** — biggest UX win, mostly Vista work
- **C (soft fallback)** — cheap, makes the empty state feel intelligent

Defer A, D, E to post-demo. They'd be lovely but the demo can survive without them if we lean on the COMPARE and successful SEARCH flows.

---

## 📋 27. Per-endpoint OpenAPI annotation pass (existing plan)

---

## 📋 19. Per-endpoint OpenAPI annotation pass (existing plan)

The plan in `~/.claude/plans/thank-you-add-functionality-purrfect-dragon.md` (already exists) walks 47 endpoints adding `@Operation` + `@ApiResponses` + persona references. Highest-value for rubric category **System Design & Docs (15%)**.

Optional — only if we have time after the other items.
