# Demo Claims Audit — May 26 Presentation

Audited the Haven codebase against the 16 claims in the demo script. Honest verdicts below — IMPLEMENTED / MOCKED / MISSING — with evidence and recommended action.

---

## ✅ 1. Persona-driven Bruno test simulations — PARTIALLY IMPLEMENTED

**Evidence:** `audit/bruno/` has dedicated collections for **Amaka, Biodun, Dayo, Emeka, Ngozi** plus a "Demo" master collection that orchestrates Adaeze + Babatunde inspections (`Demo/collection.bru` references them by name). **Missing dedicated persona scripts for Temi, Adaeze, Babatunde.** They appear as actors *inside* the Demo script but don't have their own folders.

**Recommendation:**
- Soften the claim to "Bruno-based test simulations covering the 6 production personas (Amaka, Biodun, Emeka, Ngozi, Dayo, and the Demo flow that walks Adaeze + Babatunde through the buyer journey)" — drop Temi from the claim unless you spin up a quick Temi collection.
- **Quick fix (~30 min):** copy the Ngozi collection, swap her email/name for Temi's, ship it. Same for Adaeze/Babatunde if you want full per-persona coverage.

---

## ✅ 2. Gov ID upload + admin review + status update — IMPLEMENTED

**Evidence:**
- `POST /api/verifications/documents` accepts multipart files and uploads to R2 via `R2VerificationDocumentStorage` (`software.amazon.awssdk.services.s3.S3Client.putObject`)
- `POST /api/verifications` creates a PENDING verification row referencing the uploaded URL
- `AdminVerificationController` exposes the queue + approve/reject endpoints
- On approve: `User.identityVerifiedAt` (or `Property.documentsVerifiedAt`) gets stamped with the approval timestamp

**Recommendation:** This is the strongest claim in the script. Push it confidently. Show the full flow in the demo if there's time.

---

## ❌ 3. Liveness check for verification — MISSING ENTIRELY

**Evidence:** Searched the entire codebase for `liveness`, `face match`, `biometric`. **Zero matches** for identity-document liveness. The only "liveness" hits are Kubernetes/load-balancer health probes (`actuator/health`) — completely unrelated to identity verification.

**Recommendation:**
- **DROP THIS CLAIM ENTIRELY.** It is not built. There is no stub. The script is over-claiming.
- If you want to mention it: say "liveness check is a phase-2 feature; the platform is designed to integrate with a 3rd-party KYC provider (Smile ID, Dojah, Sourcefin) — endpoint scaffolding pending."
- **DO NOT** stand on stage and say a liveness check exists. A judge with a verification background will ask to see it and the demo collapses.

---

## ❌ 4. "Possible Scam" warning label on unverified listings — MISSING

**Evidence:** Zero matches for `possibleScam`, `unverifiedWarning`, `warningLabel` in code. The `SCAM` reason exists in `ReportReason` enum (a user can *report* a listing as scam), but no UI signal automatically labels unverified-owner listings as suspicious.

**Recommendation:**
- **Three options, in order of effort:**
  1. **Drop the claim** — say "unverified owners simply don't have the verification badge, which functions as the trust signal in the inverse" (true today)
  2. **Vista-only fix (~1 hour)** — Vista can read the existing `identityVerifiedAt: null` field and slap a "Identity not verified" warning chip on the listing card. No backend change needed.
  3. **Backend + Vista (~2 hours)** — add a computed `trustWarnings: ["UNVERIFIED_OWNER"]` field to the listing detail response so Vista doesn't have to derive it from multiple fields
- My recommendation: **Option 2** before May 26. It matches the script's claim, takes an hour of Vista work, no backend risk.

---

## ⚠️ 5. No badge for verified, only premium gets a badge — MISSING (premium tier doesn't exist)

**Evidence:** Zero matches for `premium`, `Premium`, `premiumVerified` anywhere in code, config, or migrations. There is no premium tier in the system.

What DOES exist: a single `identityVerifiedAt` timestamp on the User entity (and `documentsVerifiedAt` on Property). Vista can use that to render a badge or not. There is exactly one verification tier, and it's the basic identity verification covered by claim 2.

**Recommendation:**
- **Drop the "premium" framing.** The honest framing is: "Verified owners get a trust badge (verified identity timestamp); the badge is the primary trust signal in the product. Premium tiers are roadmap, not built."
- Restructure the script to say: "Verified owners get a trust badge. Unverified owners do not — Ngozi reads this signal before deciding to engage."

---

## ✅ 6. PostgreSQL GIST exclusion constraint for slot uniqueness — IMPLEMENTED

**Evidence:** `src/main/resources/db/migration/V8__add_inspection_slot_overlap_constraint.sql`:
```sql
ALTER TABLE inspection_slots
    ADD CONSTRAINT inspection_slots_no_overlap
    EXCLUDE USING gist (
        listing_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    );
```
Plus a partial unique index on `inspection_requests (slot_id) WHERE status IN ('PENDING','APPROVED')` for the booking race.

**Recommendation:** Push this hard. It is your strongest "hardest part" demo answer. Two race-free invariants at the database layer.

---

## ✅ 7. INSPECTION_REQUESTED Kafka events keyed by listing ID — IMPLEMENTED

**Evidence:** `InspectionService.requestSlot()` line 129: `.partitionKey(String.valueOf(listing.id()))` — explicitly partitioned by listing ID. Topic is `inspection.requested.v1`. Consumed by `InspectionRequestedListener` with manual ack discipline.

**Recommendation:** Implemented and correctly keyed. Push.

---

## ✅ 8. OFFER_SUBMITTED Kafka events keyed by listing ID — IMPLEMENTED

**Evidence:** `OfferService.submit()` writes `OfferSubmittedEvent` to outbox, topic `offer.submitted.v1`. Consumed by `OfferSubmittedListener`. Same outbox/relay pattern as inspections.

**Recommendation:** Implemented. Push.

---

## ✅ 9. Transactional outbox pattern — IMPLEMENTED

**Evidence:** Both `OfferService` and `InspectionService` write to `OutboxEvent` in the same `@Transactional` boundary as the domain row insert. `OutboxRelay` (a separate component) drains the outbox table to Kafka. Both inserts commit-or-fail together; the publish happens asynchronously after commit.

**Recommendation:** Push. This is a real engineering credibility point.

---

## ❌ 10. Agent dashboard real-time notifications — MISSING (KNOWN GAP)

**Evidence:** Both `InspectionRequestedListener` and `OfferSubmittedListener` only call `notificationApi.recordAsync(eventId, ..., event.ownerId(), event)` — **they notify the OWNER only**, never the assigned agent. The service-level comment in `InspectionService` literally says "async Kafka fanout to owner + agent" but the implementation doesn't deliver the agent half.

**Recommendation:**
- This is already documented as **Item 7 Gap A** in `post-session-tasks.md`. Tracked, not surprise.
- **Implementation: ~1 hour total** — in each listener, look up `listingService.activeAgentUserId(listingId)` and call `recordAsync` a second time if non-null. Plus tests.
- **DO IT BEFORE MAY 26.** Real-time agent notifications is a credibility point in the script and it's a 1-hour fix.
- If you can't fix it: soften the claim to "Owner dashboards receive real-time notifications; agent dashboards are a planned phase-2 extension that reuses the existing outbox path."

---

## ✅ 11. Community reporting button — IMPLEMENTED

**Evidence:** `listingreport` module has `ListingReportController` (user-facing) + `AdminListingReportController` (admin-facing) + repository + service. `ReportReason` enum has SCAM, MISLEADING, INAPPROPRIATE, OFFENSIVE, OTHER. Reports surface in the admin queue.

**Recommendation:** Push. Backend is complete. Verify Vista has wired the button.

---

## ✅ 12. Admin moderation queue functional — IMPLEMENTED

**Evidence:** `AdminListingController` has `approve` + `takedown` endpoints. `AdminListingReportController` shows the report queue. `AdminUserController` has suspend/reactivate. `AdminVerificationController` has the approve/reject queue. `AdminAuditLogController` shows the full action trail. Every admin action writes to `admin_audit_log` in the same transaction.

**Recommendation:** Push hard. This is one of the most complete surfaces in the system.

---

## ⚠️ 13. Natural-language search ("2-bedroom in Surulere under 2 million with verified owners") — PARTIALLY IMPLEMENTED

**Evidence:** Dream AI search is real and works:
- pgvector embedding NN finds candidates from a few thousand listings
- Anthropic Claude Haiku ranks the bounded catalogue
- Returns a list of LIVE-checked listing IDs

**BUT:** the catalogue JSON sent to Claude (`buildListingsArrayJson` in `DreamAiService`) does NOT include verification status. So Claude *cannot* filter on "verified owners". It can match "2-bedroom", "Surulere", "under 2 million" — those are in the catalogue. Verification status would be ignored.

It is also NOT text-to-SQL. It's text → embedding → ranked by LLM.

**Recommendation:**
- Soften the example: drop "with verified owners" from the on-stage prompt. Demo with "2-bedroom in Surulere under 2 million naira" — that works end-to-end today.
- Or **quick fix (~1 hour):** add `ownerVerified: bool` to the catalogue JSON and update the system prompt to filter on it. Tested.
- Stop saying "text-to-SQL" — that's a different architecture you didn't build. Say "natural-language search backed by vector embeddings and a Claude-Haiku-based reranker."

---

## ⚠️ 14. Deployed and accessible (Haven on Railway, Vista on Vercel) — HAVEN UP, VISTA UNCERTAIN

**Evidence:**
- `https://haven.dreamhomes.today/actuator/health` returns HTTP 200 ✅
- `https://vista.dreamhomes.today/` returns HTTP 404 ❌
- `https://vista.dreamhomes.today/listings` returns HTTP 404 ❌

Vista is at least DNS-resolved (the 404 came from a server, not a connection error), but **the home page and listings page both 404** when I curled them from this audit. Either Vercel isn't serving, the routing is wrong, or the curl is missing a header Vista's edge requires.

**Recommendation:**
- **URGENT — verify Vista is actually live in a browser TODAY.** Don't assume because Silas said it was deployed. If those 404s repro in a browser, something's broken.
- If Vista is broken: triage with Silas immediately. This is your demo surface; cannot demo without it.
- If Vista works in a browser (curl-specific issue): note it but no action.

---

## ⚠️ 15. CI/CD via GitHub Actions — IMPLEMENTED BUT MISMATCHED TARGET

**Evidence:** `.github/workflows/ci.yml` — runs `mvn verify` on every push/PR with Testcontainers + JDK 21 ✅. `.github/workflows/deploy-eks.yml` — auto-deploys to AWS EKS on push to main.

**The conflict:** The `deploy-eks.yml` deploys to **EKS** but the actual production URL is `haven.dreamhomes.today` which (per Session 1 context + Railway env vars) is hosted on **Railway**. So one of:
- The EKS workflow runs but Railway is the active production (EKS deploy is dead-letter)
- OR you've migrated to AWS and the docs / Session 1 doc are stale
- OR both run in parallel for now

**Recommendation:**
- Confirm with Silas which deploy target is **active**. Demo-day pitch should describe the actual deploy story, not both.
- The CI half (mvn verify on PR) is solid and unambiguous — push that part.
- For the deploy claim: say "auto-deploys to AWS via GitHub Actions" (truthful for EKS workflow) OR "auto-deploys to Railway from main" (whichever is actually live).

---

## ✅ 16. Demo passwords documented — IMPLEMENTED

**Evidence:** `DemoDataSeeder.java` line 84-85:
> *"Bcrypt-10 hash of `Demo2026!`. Pre-computed so the seeder doesn't pay BCrypt cost on every boot. Same password the Bruno collections use."*

All seeded demo accounts (including Amaka, Biodun, Emeka, Ngozi, Dayo, Adaeze, Babatunde) share the password **`Demo2026!`**. Confirmed in the code. Note: **Temi does NOT exist as a seeded user** — only the personas in `DemoDataSeeder.saveUser(...)` lines. Confirm she's in the seed before claiming you can log in as her.

**Recommendation:**
- Document the password + email list explicitly in the demo script. The convention is `<firstname>.<lastname>@demo.dreamhomes.local`.
- Verify Temi exists in the seed. If not, add her (5 minutes — copy an existing applicant line in `DemoDataSeeder`).

---

# Summary table

| # | Claim | Status | Action before May 26 |
|---|---|---|---|
| 1 | Persona Bruno scripts | PARTIAL | Add Temi collection (~30min) OR drop her from claim |
| 2 | Gov ID upload + admin review | ✅ IMPLEMENTED | Push |
| 3 | Liveness check | ❌ MISSING | **DROP CLAIM** — over-claim risk |
| 4 | "Possible Scam" warning | ❌ MISSING | Vista 1h fix OR drop |
| 5 | Premium badge tier | ❌ MISSING (no premium exists) | Restructure to "one tier, badge = verified" |
| 6 | GIST exclusion constraint | ✅ IMPLEMENTED | Push hard |
| 7 | INSPECTION_REQUESTED Kafka | ✅ IMPLEMENTED | Push |
| 8 | OFFER_SUBMITTED Kafka | ✅ IMPLEMENTED | Push |
| 9 | Transactional outbox | ✅ IMPLEMENTED | Push |
| 10 | Agent real-time notifications | ❌ MISSING (gap A from earlier audit) | **FIX in 1h** before demo |
| 11 | Community reporting | ✅ IMPLEMENTED | Push, verify Vista wired |
| 12 | Admin moderation queue | ✅ IMPLEMENTED | Push hard |
| 13 | Natural-language search | PARTIAL | Drop "verified owners" from example OR add ~1h fix |
| 14 | Deployed end-to-end | ⚠️ Haven yes, Vista 404 | **VERIFY VISTA TODAY** |
| 15 | CI/CD GitHub Actions | ⚠️ TARGET MISMATCH | Confirm Railway vs EKS with Silas |
| 16 | Demo passwords documented | ✅ IMPLEMENTED | Verify Temi is seeded |

---

# What to do before May 26 (priority order)

**MUST FIX (don't demo without these):**
1. Verify Vista is actually serving — curl returns 404 (5 min check in browser)
2. Clarify deploy target with Silas — Railway or EKS or both? (10 min)
3. Fix Gap A from `post-session-tasks.md` item 7 — agent notifications on inspection + offer events (~1 hour)
4. Confirm Temi is in `DemoDataSeeder` — if missing, add her (~5 min)

**SHOULD FIX (real wins for the script):**
5. Vista 1-hour fix to surface "Identity not verified" warning on listing cards (claim 4)
6. Drop "with verified owners" from the natural-language search demo prompt OR add the 1h backend fix (claim 13)

**MUST CUT FROM SCRIPT:**
7. The liveness check claim — does not exist, don't say it does
8. The "premium" badge tier framing — restructure to single-tier verified-badge

**MAY ADD IF TIME:**
9. Temi-specific Bruno collection (~30 min)
10. Adaeze + Babatunde Bruno collections (~30 min each)

---

# Bottom line

The system is **stronger than the script in some places** (admin moderation queue, transactional outbox, GIST constraint) and **weaker in others** (liveness check, "Possible Scam" warning, premium badge tier, agent notifications). The script reads like it was written before the final scope settled.

**Re-baseline the script against the audit before May 26.** If you say something on stage that you can't show, you lose more points than if you simply don't mention it. Cut the over-claims; lean into the parts that are genuinely well-built.
