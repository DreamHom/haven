# Likely Demo-Day Questions

A running list of questions a judge / interviewer / curious dev might throw at us, built as we walk through each session.

**Format for each entry:**
- **Q:** the question
- **A (on stage):** the punchy answer to deliver out loud
- **Explanation:** the deeper unpack — how to think about it so you actually understand, not memorise

Add to this list as new sessions land. Sort each section by *most likely to be asked* at the top.

---

## Top 7 — High-likelihood curveballs

The questions judges/seniors love to throw because they probe whether you understood the system or just memorised your script. **Drill these hardest.**

### Q: Why didn't you use OAuth instead of JWT?
**A (on stage):** That's actually two different layers conflated. OAuth is an *authorization framework* — "user X lets app A act on their behalf at service B" — and the tokens it issues can themselves be JWTs. We have first-party auth: users log in to DreamHomes directly with email + password, no third-party delegation. So we used the simplest viable thing — RS256 JWTs minted by our own auth endpoint. If we added "sign in with Google", we'd layer OpenID Connect on top, which would also issue JWTs. JWT is the token format; OAuth is the delegation flow. They're not alternatives.

**Explanation:** The question conflates two things. JWT = "tamper-proof token format". OAuth = "protocol for delegated authorization". Most OAuth implementations DO use JWTs as their access tokens — they're complementary, not competing. The senior-grade answer separates them cleanly. If the interviewer meant "why not use Auth0 / Cognito / Firebase Auth" — different question, answer that one with "cost, vendor lock-in, and JWT is something you should know how to implement yourself once before reaching for a managed service."

---

### Q: How does the GIST constraint actually work?
**A (on stage):** GIST is a Postgres index type that supports fuzzy predicates beyond plain equality — range overlaps, geometric containment, etc. We use it on the `inspection_slots` table with an EXCLUDE constraint: `EXCLUDE USING gist (listing_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)`. In English: no two rows are allowed where the listing_id matches AND the time ranges overlap (`&&` is Postgres's range-overlap operator). The `[)` notation is a half-open interval — includes the start, excludes the end — so back-to-back slots like 10–11 and 11–12 don't count as overlapping. The constraint check fires at INSERT time and rejects the row before the transaction commits. Postgres uses the GIST index to make the overlap lookup fast — without it, every insert would scan all existing rows for that listing.

**Explanation:** Three pieces working together. (1) **GIST index** = the access method that makes range queries fast. (2) **EXCLUDE constraint** = "no two rows can match this combination of predicates", more general than UNIQUE. (3) **`tstzrange` + `&&`** = Postgres's range type + its overlap operator. The half-open `[)` is the elegant bit — back-to-back slots are mathematically NON-overlapping (10:59:59.999 is in the first; 11:00:00.000 starts the second). The whole thing is structurally race-free because Postgres serialises the check at the storage layer; no two parallel inserts can both pass the check.

---

### Q: What stops a verified agent from going rogue after verification?
**A (on stage):** Three independent kill-switches plus a complete audit trail. (1) Admin can suspend the agent — bumps their `tokenVersion`, invalidates every outstanding JWT immediately, blocks future logins via `suspended_at`. (2) Admin can soft-revoke the verification badge (set the `agentCredentialVerifiedAt` to null) — they lose the public trust signal without losing their account. (3) Either party can revoke any active `agent_listings` row — instant cutoff of the agent's ability to act on a specific listing, no grandfathered access. And every admin action writes to `admin_audit_log` in the same transaction, so we always know who did what when. Honest gap: there's no automatic rogue detection — we rely on community reporting (which exists, `POST /listings/{id}/reports`) plus admin review.

**Explanation:** "Verified" is not a permanent state in Haven — it's just a timestamp that drives a badge. Trust is layered: verification badge (the public signal), agent assignment (the operational scope), and account status (the existential check). Any of the three can be revoked independently with instant effect. The honest gap to flag if they push: we don't do behavioural anomaly detection. A rogue agent who's careful could do damage between reports. Mitigation would be a fraud-scoring service — definitely roadmap, not built.

---

### Q: What's the AI doing exactly?
**A (on stage):** Two-stage retrieval pipeline. Stage 1: every listing has a pre-computed OpenAI embedding stored in pgvector; we embed the user's prompt with the same model and find the nearest ~80 listings by cosine similarity — that's our candidate set. Stage 2: we bundle those 80 as a JSON catalogue and send to Claude Haiku with a system prompt that says "rank these best-to-worst for the user's query". Claude returns `{listingIds:[...]}` ordered, and we validate the IDs against our candidate set before returning to the user. The compare flow is similar but returns structured pros/cons + a recommendation instead of a ranked list. The critical design: the LLM never decides the *outcome shape* — that's pure Java regex / length checks in the orchestrator. The LLM only fills in the body. That's what makes the system safe even with prompt injection.

**Explanation:** Classic two-stage retrieval — cheap fuzzy filter (embeddings) followed by expensive smart ranker (LLM). Same pattern as Google search. NOT text-to-SQL (we don't translate the prompt to a query). NOT autonomous (the LLM never decides what to do). NOT full-database semantic search (bounded candidate slice). Each of those clarifications is a real interviewer-trap — they'll test if you understand the boundaries. The bounded-outcome design is the security story: an attacker prompt-injecting "ignore previous instructions" can't shift the outcome shape OR exfiltrate listings not in the candidate set.

---

### Q: How would you scale to a million users?
**A (on stage):** Three bottlenecks, three fixes. (1) **API instances**: Spring Boot is already stateless (JWT auth + outbox-based events), so horizontal scale behind a load balancer is straightforward. Migrate JWT signing to AWS KMS so multiple instances don't need the private key on disk. (2) **Database**: Postgres scales vertically a long way; we'd add read replicas for browse-heavy traffic and partition `listings` by region. pgvector handles a few million vectors fine; beyond that we'd switch to a dedicated vector DB like Pinecone. Add a Redis cache layer in front of read-heavy public discovery endpoints. (3) **Notifications**: Outbox + Kafka already scales horizontally — just add more partitions. SSE doesn't scale across instances today (in-memory emitter registry), so at a million users we'd add Redis pub/sub to fan SSE events across nodes — already flagged as deferred in the `NotificationSseEmitters` Javadoc.

**Explanation:** Don't say "rewrite in microservices". Senior engineers reason about *specific bottlenecks and specific remedies*, not whole-system rewrites. Microservices solve a people problem (multiple teams stepping on each other) and a scale problem (one service spiking and you don't want to scale everything). A million users isn't either of those by itself. The three bottleneck fixes above scale the existing monolith to many millions before you'd genuinely need to split it.

---

### Q: Why these two engineering decisions over others?
**A (on stage):** Best to ask the interviewer which two they mean — but if I had to pick the two I'd defend hardest: **transactional outbox over direct Kafka writes**, because it eliminates partial-failure between DB commit and event publish without needing two-phase commit; the cost is one extra table + a relay daemon, which is tiny compared to the alternative complexity. And **the monolith over microservices**, because the entire bounded context (rental marketplace) is naturally cohesive — splitting it early would force us to solve distributed-system problems (eventual consistency, schema versioning, deploy choreography) the rubric doesn't ask for. A monolith ships faster, fails predictably, and can be split later if a specific surface needs independent scaling.

**Explanation:** The trick to this question is to NOT scramble for two decisions on the spot. Have two answers ready. Outbox + monolith is a strong pair — both are deliberate architectural calls with clear trade-offs and clear "we'd revisit when" triggers. If the interviewer pushes for different decisions: per-listing Kafka partitioning (for ordering), partial unique indexes (for the inspection + agent uniqueness), pgvector over a dedicated vector DB (scale-fit + ops simplicity), provider abstractions on KYC + LLM surfaces (vendor flexibility) are all defensible.

---

### Q: What if Kafka goes down?
**A (on stage):** The transactional outbox absorbs the impact. Domain transactions still commit cleanly because they don't talk to Kafka inline — they write to the `outbox_events` table in the same Postgres transaction. The relay then can't publish, but that's fine — it retries indefinitely. When Kafka comes back, the relay drains the backlog in order. User-facing actions don't fail; the only user-visible effect is that async notifications (cross-aggregate fan-out via Kafka listeners) land a few minutes late instead of seconds late. Sync in-tray notifications (which don't go through Kafka) still fire immediately so the actor still sees their action was received. We have `OutboxMetrics` exposing outbox depth, so monitoring would alert if the backlog grew abnormally.

**Explanation:** This is exactly the failure mode the outbox pattern is designed for. The system degrades gracefully rather than catastrophically. The honest edge case: if Kafka stays down for HOURS, the outbox table grows; if the Postgres disk fills up, then domain transactions start failing. Mitigation is disk monitoring + Postgres auto-vacuum + alerting on outbox depth. The per-listing partitioning guarantee means even after a multi-hour backlog, when Kafka comes back, events for each listing are processed in their original order.

---

## Session 1 — Product + Architecture

### Q: Walk me through the architecture.
**A (on stage):** One Spring Boot service that owns all the business logic. Persists to AWS RDS Postgres (with pgvector for AI similarity search), publishes events to AWS MSK via a transactional outbox, stores photos on Cloudflare R2, and calls Anthropic for the Dream AI assistant. Vista (Next.js) is a separate frontend; the contract between us is OpenAPI. We support two Spring profiles — `aws` for production and `railway` for local/demo backup — so the same code runs in both shapes.

**Explanation:** The shape is *monolith with cloud add-ons*. One application, four external dependencies. Walk left-to-right on the diagram: client → API → (DB / Kafka / object store / LLM). The two profiles aren't "two deployments of different code" — it's *the same jar* with different config files. That's a clean engineering pattern: code is portable, infra is config.

---

### Q: Who built what?
**A (on stage):** Two engineers. I drove feature breadth — controllers, services, Dream AI, demo seeding, most of the test suite. Silas, our senior backend, led infrastructure — the AWS migration to RDS + MSK, deployment hardening, and senior review on cross-cutting architecture.

**Explanation:** Be honest about the split. Don't claim Silas's infra work; don't undersell your own breadth. The truthful credit story shows team maturity.

---

### Q: Wait, Silas is also one of your personas — what's going on?
**A (on stage):** Same person, intentional. Customer personas walk *business flows*; Silas's lens is the *integrator* — the frontend dev wiring screens against the API. We promoted his concerns to a first-class persona because they catch gaps customer personas can't: "can I render this response in one call or do I need N+1?", "does the spec match runtime?", "is there a read for every write?". His persona caught real bugs — the 401-instead-of-400 validation issue, settings fields editable but not preloadable, listing detail needing 6 GETs.

**Explanation:** This is a *sophisticated team practice*, and judges will respect it. Most teams treat the frontend dev as "a person who consumes the API". You promoted their concerns to be **first-class user stories**. That elevates the integration layer from a footnote to a design constraint.

---

### Q: Why Spring Boot? Why not Node or Go?
**A (on stage):** Three reasons. (1) The bootcamp curriculum is JVM-focused, so it's the language reviewers expect. (2) Spring's batteries-included stance — security, JPA, Kafka, observability, validation, all in one — meant less wiring time and more feature time. (3) Static type system caught a lot of bugs at compile time in a 2-week sprint with no QA.

**Explanation:** The honest reason is "the bootcamp told us to use Java". But the *good* reason is the productivity story — Spring gives you so many cross-cutting concerns out of the box (RBAC, validation, exception handling, observability) that you can ship more features per day. Don't pretend you evaluated Go and rejected it; just own why Spring is right for *this* project.

---

### Q: Why a monolith and not microservices?
**A (on stage):** The whole bounded context — listings, browsing, engagement, offers, moderation — is naturally cohesive. Splitting it would mean solving distributed-system problems (eventual consistency, schema versioning, deploy choreography) the rubric doesn't ask for. Monoliths ship faster, fail predictably, and can be split later if a specific surface needs independent scaling.

**Explanation:** This is the "premature optimisation" answer. Microservices solve a *people problem* (multiple teams stepping on each other) and a *scale problem* (one service traffic-spikes and you don't want to scale the whole thing). Two devs, no traffic problem yet — monolith wins on every axis.

---

### Q: Why did you migrate from Railway to AWS?
**A (on stage):** Railway's bundled-Postgres approach wiped the database on every redeploy — fine during dev, not OK for anything production-shaped. Silas led the migration to AWS RDS for durable storage and MSK for managed Kafka. We kept the Railway profile as a fallback for local dev and demo backup.

**Explanation:** The Railway shape was a *cost-saving hack* — fine for prototype, wrong for production. The migration is essentially "we grew up". Mention that Railway is *still there as a fallback profile* — that shows you didn't just delete the old thing, you kept it portable. Engineering maturity.

---

### Q: How do you switch between AWS and Railway?
**A (on stage):** Spring profile. `--spring.profiles.active=aws` or set `HAVEN_PROFILE=aws`. Same application code, different `application-<profile>.yml`. Production runs `aws`; local dev or demo backup runs `railway`.

**Explanation:** Spring profiles are like environment-specific config files. Spring auto-loads `application-<profile>.yml` when you activate that profile. *Nothing in the Java code changes* — just which `.yml` file's values get bound to the `@ConfigurationProperties` beans.

---

### Q: What was the hardest part?
**A (on stage):** The inspection slot model — getting a DB-level guarantee that no two requests can claim the same slot, without any application-level locking. The fix was Postgres' `EXCLUDE USING GIST` constraint on `(slot_id WITH =)` for active requests only.

**Explanation:** This is a great answer because it's a specific, concrete, technically-interesting problem with a clean solution. Avoid "deployment was hard" or "Spring docs are confusing" — those make you sound junior. Specific technical wins make you sound senior.

---

### Q: What's the deploy story?
**A (on stage):** `git push origin main` → AWS pipeline picks up the change → builds + deploys → restarts. Health check at `/actuator/health`. Flyway migrations run automatically on startup. The Railway fallback is still wired up if AWS has an outage during the demo.

**Explanation:** [Refine with Silas's actual AWS pipeline details — ECS task definition? GitHub Actions? CodeDeploy?] The key talking points are: zero-touch deploys, health checks, automatic migrations, and a fallback. That's a real-shaped deploy story, not "we SSH in and run mvn".

---

### Q: What would you change with another month?
**A (on stage):** Three things. (1) KMS-backed JWT signing so the private key can't be extracted even from a compromised host. (2) An external moderation classifier on Dream AI instead of a substring block list. (3) Split Dream AI ranking into a background job so users get true token-by-token streaming instead of "wait for the whole markdown then chunk it".

**Explanation:** Always have a "what's next" list. It shows you know your own gaps. Pick *concrete* items, not vague ones ("better tests" is vague; "anomaly detection on admin actions" is concrete).

---

### Q: Why Postgres + pgvector instead of a dedicated vector DB?
**A (on stage):** Dream AI's similarity search runs over a few thousand listings — Postgres handles that fine, and we already operate Postgres. No new bill, no new system to learn, no new failure mode. If we ever scale to millions of listings we'd revisit.

**Explanation:** "Use one less tool" is almost always the right answer at small scale. Vector DBs are amazing when you have billions of vectors. At our scale, pgvector is a single Postgres extension that adds no operational complexity. The trade-off triggers a revisit *if* scale changes.

---

## Session 2 — Auth + Identity

### Q: What does "stateless RS256 JWT" mean?
**A (on stage):** Stateless means the server doesn't keep a list of who's logged in — all the proof is inside the token itself, and we just verify the signature on every request. RS256 means we sign with an asymmetric key pair: a private key the server uses to mint tokens, and a public key any verifier could use to check them. Together: scales horizontally with no shared session store, and the key material is split so the verifying side never needs the signing side's secret.

**Explanation:** Two ideas glued together. *Stateless* = "the token is the proof, not the lookup". *RS256* = "sign with one key, verify with another, only the signing one is sensitive". The combo means a load balancer in front of 10 instances doesn't need any shared memory — each instance verifies independently.

---

### Q: What happens if your JWT private key leaks?
**A (on stage):** Catastrophic. Anyone with the private key can mint a valid JWT as any user, including admin. The mitigation is bounded blast radius: short access TTLs (1h), and a clean rotation story — regenerate the keypair, restart the service, every old token becomes invalid, every user re-logs in. No persistent compromise. The thing we'd add next is KMS-backed signing so the key physically can't be extracted from a compromised host.

**Explanation:** Be honest: a leaked private key is bad. The strong answer isn't "it can't leak" (it always could) — it's "here's our defence-in-depth, here's our recovery, here's what we'd build next". Judges love that you know the limits of what you shipped.

---

### Q: Couldn't the `tokenVersion` check stop a forged JWT after a key leak?
**A (on stage):** No, and it's worth being honest about this. If an attacker has the private key they can forge JWTs with *any* `tv` value they want — they just read the user's current tokenVersion (or guess) and put that number in the claims. `tv` defends against *stolen real tokens after a logout*, not against *forged tokens by someone with the key*. Once the key leaks, only key rotation restores safety.

**Explanation:** This is a subtle point most candidates miss. `tokenVersion` is brilliant for "log this user out everywhere", terrible for "stop a forged token". The strong answer separates the two threats clearly.

---

### Q: How do refresh tokens work in your system?
**A (on stage):** Two-token model. Access JWT lives 1 hour, refresh token lives 30 days. When the access JWT expires, the frontend silently calls `POST /auth/refresh` with the refresh token, gets a new access + a new refresh, and the old refresh is immediately revoked. We use **rotation** — each refresh is single-use — and **replay detection** — if a revoked refresh is ever presented again, we kill the entire chain of descendant tokens and force a fresh login.

**Explanation:** Three layered ideas: (1) refresh exists for UX so users don't re-login every hour, (2) rotation makes a stolen refresh useful for at most one use, (3) replay detection catches silent theft by triggering on the moment two parties try to use overlapping tokens. Each piece reinforces the others.

---

### Q: Why are access tokens JWTs but refresh tokens stored in a DB?
**A (on stage):** Different goals. Access tokens are JWTs because we want *fast verification* — no DB lookup, just signature check, every request. Refresh tokens are DB rows because we want *revocability* — you can't easily revoke a JWT, but you can `DELETE FROM refresh_tokens WHERE token = ?` instantly. Best of both: fast access, revocable sessions.

**Explanation:** This is one of those "obvious in hindsight" architectural decisions. JWTs and DB rows aren't competitors — they're complementary, used where each shines. Mention this if asked "why not JWTs for everything?".

---

### Q: How do you log out a JWT?
**A (on stage):** Two strategies for two scenarios. `jti` blocklist for "log out this device only" — we add the specific token's unique ID to a small revoke table, and the auth filter rejects anything matching. `tokenVersion` bump for "log out everywhere" — we increment a number in the user row, the filter compares it to the `tv` claim in every JWT, and any token with a stale `tv` dies. Surgical vs nuclear.

**Explanation:** The clever bit is that *both run on every request*. So we get fine-grained control (kill one device) AND nuclear option (kill all devices) without conflict. Spring's auth filter chain handles both checks in microseconds.

---

### Q: When does the `tokenVersion` bump fire?
**A (on stage):** Four cases. (1) Logout with `?scope=all`. (2) Password change. (3) Password reset via email. (4) Admin suspends the account. Each of these is "kill every existing session for this user".

**Explanation:** The common thread is *"the account itself is in a new state"*. If the password changed, all old sessions might be compromised. If the admin suspended you, we want you out *now*, not when your token expires. The `tv` bump is the universal kill-switch.

---

### Q: What is enumeration and how do you defend against it?
**A (on stage):** Enumeration is the attacker probing your API to figure out which emails are real accounts — without ever logging in. The defence is response uniformity: `POST /auth/register` and `POST /auth/forgot-password` both return `202 Accepted` regardless of whether the email exists. The attacker gets the same response either way, so they can't extract any information about who's a user.

**Explanation:** Most APIs leak this information without realising — "email already taken" on registration is a classic. By always returning the same thing, we close the recon channel entirely. Small UX cost ("did my registration succeed?"), big security win.

---

### Q: How does rate limiting work?
**A (on stage):** We use Bucket4j on all `/api/auth/*` POSTs. Each IP has a bucket holding 30 tokens that refills 30 tokens per 60 seconds. Each request costs 1 token. When the bucket is empty, we return 429 Too Many Requests with a `Retry-After` header — *before* the request even reaches bcrypt. That turns a 10-million-attempt brute force into a 23-year campaign.

**Explanation:** The math sells it. Bcrypt slows each attempt to ~100ms. Without rate limiting, attacker = 10/sec = a million tries an hour. With 30/min cap, attacker gets 1800/hour. The cap kills the attack economy.

---

### Q: How do controllers enforce roles?
**A (on stage):** `@PreAuthorize` annotations on controller methods. The JWT filter populates a `SecurityContext` with the user's role; `@PreAuthorize("hasRole('OWNER')")` checks that context before the method runs. Pass → method runs. Fail → 403 Forbidden, method never executes.

**Explanation:** Two halves: (1) the filter puts the *role* into a request-scoped context, (2) the annotation reads it. Decoupled and reusable. You can mix `@PreAuthorize("hasAnyRole('OWNER','AGENT')")` for OR, `isAuthenticated()` for "any logged-in user", `permitAll()` for public.

---

### Q: Why does Spring's JWT filter run on every request, even public endpoints?
**A (on stage):** So public endpoints can *also* know who's logged in if they want to. The filter unconditionally tries to parse + verify any JWT, populating the SecurityContext if it succeeds. The "is auth required here?" decision happens later via `@PreAuthorize`. That lets a public listing endpoint show personalised content to logged-in users *and* fall back to anonymous for guests, without two code paths.

**Explanation:** Different from NestJS's per-route Guards. Spring takes the "always populate, decide later" approach. The benefit shows up in endpoints like browse listings — works for anon, but if you're logged in we know your saves and can mark which listings you've already saved.

---

### Q: How does your auth compare to a NestJS setup?
**A (on stage):** Same family, different vocabulary. Spring's `JwtAuthenticationFilter` ≈ NestJS's `JwtStrategy` + `JwtAuthGuard`. `@PreAuthorize` ≈ `@Roles` + `RolesGuard`. `@AuthenticationPrincipal` ≈ `@CurrentUser()`. Biggest difference: Spring runs the filter unconditionally on every request; NestJS only runs guards on routes that opt in with `@UseGuards()`.

**Explanation:** This is a good fluency answer if a judge with Node background asks. It shows you understand the *patterns*, not just the framework. Auth design problems are the same everywhere — the framework just changes the names of the bits.

---

### Q: How do you handle "log out everywhere" vs "log out this device"?
**A (on stage):** The logout endpoint takes a `?scope=` parameter. `scope=device` (default) adds the current JWT's `jti` to the blocklist — only this device dies. `scope=all` bumps the user's `tokenVersion` — every JWT they have anywhere dies on next use. Two separate mechanisms; one endpoint surface.

**Explanation:** Cleanest single-endpoint design. The frontend picks the scope based on the user's intent — "log out" button → device, "log out all sessions" button (in settings) → all. Two intents, one URL.

---

## Session 3 — Property + Listing Lifecycle

### Q: Why are Property and Listing separate tables instead of one?
**A (on stage):** Because they have different lifespans. A property is the physical asset — exists for decades. A listing is one offer or availability of that asset — usually weeks to months. One property typically has multiple listings over time: rented in 2024, re-listed in 2025, sold in 2026. If they were one row, we'd lose rental history on every re-list, lose verification badges, and re-publishing would mean creating a fake "new property".

**Explanation:** This is a *modelling* decision, not a tech decision. The rule of thumb we used: **if it's still true after the tenant moves out, it's on Property; if it only describes this offer, it's on Listing**. Address, bedrooms, owner-identity verification → Property. Price, RENT vs SALE, status, headline → Listing.

---

### Q: Walk me through the listing state machine.
**A (on stage):** Four states: DRAFT, OPEN, CLOSED, TAKEN_DOWN. Owner publishes a DRAFT → OPEN. When an offer is accepted, OPEN → CLOSED auto-flips in the same transaction. Admin can move OPEN → TAKEN_DOWN if the listing breaks rules, and admin can reverse it back to OPEN. Illegal transitions return 409 Conflict — you can't unrent a property, you can't shortcut DRAFT to CLOSED.

**Explanation:** State machines protect *invariants*. Other parts of the system rely on these states — only OPEN listings appear in browse, offers can only be accepted on OPEN, reviews only on CLOSED. If listings could be in any state at any time, every consumer would need to re-verify everything. Centralising the rules in one place keeps the whole system honest.

---

### Q: How do you stop two people from overwriting each other's edits on the same listing?
**A (on stage):** Optimistic locking via Hibernate's `@Version`. We add a `version` column to the listing row, embed it in the read response, and require it on every update. When a write happens, Hibernate's UPDATE includes `WHERE id = ? AND version = ?` — if anyone updated the row in between, zero rows match, Hibernate throws `OptimisticLockException`, and we return 409 Conflict with "this was modified by someone else, refresh and retry". No app-level locks, no DB-level locks, no blocked reads.

**Explanation:** "Optimistic" means we *assume conflicts are rare* — for a real-estate listing where owner and agent rarely edit at the same time, that's true. Pessimistic locking (lock-on-read) would be wasteful here. The trade-off: occasional retries when conflicts do happen. Worth it for the simpler, faster common path.

---

### Q: How do photos work in your system?
**A (on stage):** Three paths. (1) When an owner uploads, the browser POSTs a multipart file to our API, we proxy it to Cloudflare R2, and we store the public R2 URL in the database. (2) The demo seed inserts pre-existing Unsplash URLs directly — no R2 round-trip. (3) When anyone views a listing, the browser fetches the photo bytes directly from R2's CDN — Haven never touches read bytes. Cloudflare R2 specifically because it's S3-API-compatible but has **zero egress fees** vs S3's $0.09/GB — critical for an image-heavy product.

**Explanation:** The honest gotcha: uploads currently proxy through our server (bandwidth + memory cost), instead of using pre-signed URLs (browser uploads direct to R2). It works fine at our scale (~50 uploads/day). The "what we'd change at scale" answer is: switch to pre-signed PUT URLs at >100 concurrent uploads — already designed, partially scaffolded (see `docs/photo-upload-architecture.md`).

---

### Q: How does the same listing endpoint return different data to different users?
**A (on stage):** The endpoint is public — anyone can hit it. But the service method inspects the `JwtPrincipal` (which the auth filter populated whether the caller was logged in or not) and layers fields onto the response. Anonymous → public fields only. Logged-in applicant → adds `savedByMe`. Owner → adds owner-only fields like ownerEmail, pendingOfferCount, version. Admin → adds moderation fields like takedownReason. One URL, four response shapes.

**Explanation:** This is Silas-the-integrator's "render the screen in one call" principle. Alternative would be three separate endpoints — public, owner, admin — but then the frontend would need conditional fetches based on "is this mine?" *before* knowing the answer. Layered single-endpoint = one round trip, server already knows everything about the caller.

---

### Q: How do you enforce that only the owner of a *specific* listing can edit it (not just any owner)?
**A (on stage):** Two layers. `@PreAuthorize("hasRole('OWNER')")` on the controller method checks the *role* — they must be some kind of owner. Then inside the service, before any mutation, we check `if (!listing.getOwnerId().equals(me.userId())) throw new ForbiddenException()` — that's the resource-ownership check. The role check is general, the ownership check is specific.

**Explanation:** Role checks and resource ownership are two different concerns and they need different mechanisms. Spring's `@PreAuthorize` is great for "must have role X" — but it can't easily express "must own resource Y" without complex SpEL. So we do role at the annotation, ownership in the service. Two-tier defence: even if `@PreAuthorize` is misconfigured, the service check still catches a foreign-owner edit attempt.

---

## Session 4 — Inspections + Offers

### Q: What happens when an offer is accepted?
**A (on stage):** A three-step atomic cascade in one transaction. (1) The accepted offer flips to ACCEPTED. (2) Every other PENDING offer on that listing auto-declines and a notification fires to each loser ("ANOTHER_OFFER_ACCEPTED"). (3) The listing itself auto-closes — `OfferService` calls a `forceStatus()` method on `ListingService` that bypasses the normal owner-only edit path because the offer service has already verified the caller is authorised.

**Explanation:** Auto-decline siblings was a persona-audit catch — Biodun noted that without it, losing applicants never find out the deal is done and the pending rows sit forever. Auto-close the listing was the same audit — owners were forgetting to manually close listings, which kept attracting new offers after the deal was sealed. Both behaviours are now mandatory in the same transaction so you can't get a half-state.

---

### Q: How do you prevent two applicants from booking the same inspection slot?
**A (on stage):** Two database-level guarantees. First, a Postgres `EXCLUDE USING GIST` constraint on `inspection_slots` stops the owner from publishing two slots that overlap in time on the same listing — the constraint compares time ranges with `&&` and rejects any insert that overlaps an existing one. Second, a partial unique index on `inspection_requests (slot_id) WHERE status IN ('PENDING','APPROVED')` enforces at most one active request per slot. When two applicants race, Postgres serialises the check at the index layer; one wins, the other gets a duplicate-key error which we translate to 409.

**Explanation:** This is the "hardest part" demo answer because both invariants are enforced *at the database layer, not in application code*. App-level checks have race windows; Postgres constraints don't. The half-open `[)` range notation lets back-to-back slots (10–11, 11–12) coexist because the first ends right before the second begins.

---

### Q: How does the slot self-heal after a declined or cancelled request?
**A (on stage):** The partial unique index only counts rows where status is PENDING or APPROVED. When a request goes DECLINED or CANCELLED, it drops out of the index and the slot is free again. So the same slot can cycle through many bookings over its lifetime — Ngozi booked and declined, Temi booked and the owner approved — and at every moment, only one active request exists.

**Explanation:** This is elegant because the same mechanism that prevents double-booking also enables re-trying. A normal UNIQUE constraint would have burned the slot forever after the first decline. The partial index is the difference between "uniqueness ever" and "uniqueness right now".

---

### Q: Can an owner accept an inspection request they previously declined?
**A (on stage):** No — once a request is DECLINED, that row is terminal. The `transitionFromPending()` method requires the current status to be PENDING for any transition. But the slot itself is free (because DECLINED drops out of the partial unique index), so the applicant can submit a fresh request and the owner can approve that new one. One extra click, but the audit trail stays clean — no toggle-bug confusion about whether the owner truly approved or accidentally flipped.

**Explanation:** Each decision atomic and traceable. The cost is one extra interaction; the win is no ambiguous "I approved… actually declined… actually approved again" history.

---

### Q: Can an applicant cancel an inspection after it's been approved?
**A (on stage):** No — and this is a known gap. The `cancel()` method requires status=PENDING and only the applicant can call it. After APPROVED, neither party has a cancel path. If the applicant has a work emergency, they're forced into a no-show on their record. We've flagged this for a follow-up: extend `cancel()` to allow APPROVED → CANCELLED for both sides with notification to the other party.

**Explanation:** Honest gap to mention if asked. Shows we audit our own assumptions and don't just describe what should be there — we know what IS there.

---

### Q: When an applicant books a slot, who gets notified?
**A (on stage):** Today: the applicant gets a sync in-tray ack immediately, and the owner gets an async notification via Kafka through the transactional outbox. The assigned agent does NOT get notified — that's a gap we found during the Session 4 audit. The Kafka listener (`InspectionRequestedListener`) reads only the owner from the event, even though the service-level comment mentions "fanout to owner + agent". Two-line fix: in the listener, look up `activeAgentUserId(listingId)` and notify the agent too if non-null.

**Explanation:** This is a great honest gap to bring up. It shows you walked through the actual code, not the documentation, and noticed the mismatch. Judges love that kind of self-audit.

---

### Q: How do counter-offers work?
**A (on stage):** Each offer has an optional `parent_offer_id` column. When someone counters, the parent flips to COUNTERED (terminal) and a new child offer is inserted with `parent_offer_id` pointing back. The chain reads like a negotiation transcript — Temi offers ₦7m, Amaka counters ₦8m (parent=42), Temi counters ₦7.5m (parent=43), and so on. Every row stays in the DB; you can walk from any offer back to its parent to see the full history.

**Explanation:** This is the data-modelling angle — using a self-referential foreign key to encode a temporal sequence. Same pattern as comment threads (`parent_comment_id`). Clean because nothing is mutated except the parent's status flip; the history is purely additive.

---

### Q: How do you enforce turn-taking in offer negotiations?
**A (on stage):** Every offer row records `proposedByUserId`. The rule is one line: "you can't accept, decline, or counter an offer that YOU made". The check `if (offer.proposedByUserId.equals(callerId)) throw CannotActOnOwnOfferException()` enforces it. That single rule produces alternating turns automatically — whoever just spoke can't respond to themselves; the other party has to act. No state machine for "whose turn is it" needed.

**Explanation:** Elegant because one column + one check replaces what a more naïve design would build as a turn flag, a state machine, or polling logic. The information was already there in `proposedByUserId`; we just had to use it.

---

### Q: Can an agent negotiate offers on behalf of an owner?
**A (on stage):** Yes — if the agent's assignment is in ACCEPTED status. The `canNegotiateOffer()` check accepts three identities: the owner, the applicant, or an agent with an ACCEPTED assignment on the listing. Same endpoint, same turn-taking rule, the applicant doesn't even need to know whether they're talking to the owner or the agent. If the owner later revokes the agent's assignment, that ability disappears the moment the revoke commits — no grandfathered access.

**Explanation:** Delegation without privilege escalation. The agent acts as the owner only on the surfaces the owner explicitly delegated, and only while the delegation is active. Revoke = immediate cutoff.

---

## Session 5 — Engagement (Saves, Comments, Reviews)

### Q: Why are saves idempotent (instead of returning 409 on duplicate)?
**A (on stage):** Three reasons. Frontend doesn't need to track prior state — just call `POST /save` when the heart is tapped, server is the source of truth. Network retries on flaky connections don't fail (timeout then retry = still works). And double-clicking the heart icon doesn't error. The principle: state-setting operations should describe the desired state ("saved"), not the delta ("save count + 1"). The first is idempotent by design.

**Explanation:** This is a small but real engineering principle — REST verbs that *set state* should be safe to retry. PUT is famously idempotent for this reason. Our POST /save behaves the same way: it's really "ensure this listing is in your saves" rather than "create a new save".

---

### Q: How do comments work in your system?
**A (on stage):** Today they're flat — no threading. Anyone signed in can post a comment on any listing; comments display chronologically. Deletes are soft: the row stays with `deleted_at`, `deleted_by_user_id`, and `deletion_reason` set together (a CHECK constraint enforces all three or none). Public reads filter `WHERE deleted_at IS NULL`. The row stays for forensic + appeal purposes — admins can see exactly what was said and who removed it. Comment flagging (users reporting abuse) exists in the backend already, just not wired into the UI yet.

**Explanation:** Threading is a known gap — Vista wants it, the backend hasn't built `parent_comment_id` yet. Flat comments + soft delete is the current state. The CHECK constraint is a small but important detail — it makes "half-deleted" rows structurally impossible.

---

### Q: Who can write a review?
**A (on stage):** Strictly bidirectional between owner and accepted-applicant on a CLOSED listing. The owner can review the buyer/renter; the buyer/renter can review the owner. The check uses `offerService.hadAcceptedOffer(listingId, userId)` to verify the deal actually happened. Plus a DB unique constraint on `(listing_id, reviewer_id, reviewee_id)` means one review per direction. Rating must be 1–5; body can't be blank.

**Explanation:** This gate matters because reviews are what unfamiliar users (Ngozi) read before deciding to trust a stranger. If anyone could post reviews, the system would be gamed — competitor agents leaving fake 1-stars, scammers buying 5-stars. Tying reviews to actual transactions makes that economically expensive.

---

### Q: Can agents be reviewed?
**A (on stage):** Honest answer: no, and it's a gap. The eligibility check requires `revieweeId == listing.ownerId()`. So Emeka the agent can do all the work on a deal — showings, negotiation, key handover — but applicants can only review Amaka the owner. We've flagged this for a follow-up; the fix is to extend the eligibility to accept agents with an ACCEPTED `agent_listings` row as valid reviewees.

**Explanation:** Good to surface as an honest audit finding. Shows we walked the code instead of just describing the documented intent. The fix is well-bounded — a few extra lines in `ReviewService.post()` plus aggregate-by-role logic.

---

### Q: How does the star rating update when a review is deleted?
**A (on stage):** Reviews are soft-deleted (`deleted_at` set together with `deleted_by_user_id` and `deletion_reason`). The aggregate query for a user's average rating filters `WHERE deleted_at IS NULL`. So the next call after a deletion naturally excludes the row — the star rating drops on the next page load. No background recompute job, no cached aggregate to invalidate.

**Explanation:** Soft delete pays off twice here: (1) auditability — the row stays for appeals, (2) automatic aggregate correction — no extra machinery to keep rating consistent. The cost is one filter clause on the read query.

---

### Q: Who can delete a review?
**A (on stage):** Two paths. The review's author can delete their own (no reason required). An admin can delete any review (reason required for the audit log). Anyone else gets 403 `NotAuthorisedToDeleteReviewException`. Admin deletes also write an `admin_audit_log` row (action `REVIEW_TAKEDOWN`) — same compliance trail as other admin actions.

**Explanation:** The author-can-delete path is a self-service exit. The admin path is moderation with audit. Required-reason on admin deletes means there's always a traceable why; the optional-reason on author deletes respects autonomy ("I just changed my mind").

---

## Session 6 — Agent Assignments

### Q: How do agent assignments work?
**A (on stage):** A row in `agent_listings` with one of four statuses: REQUESTED, ACCEPTED, DECLINED, REVOKED. Owner invites an agent → REQUESTED. Agent accepts → ACCEPTED (active). Agent declines → DECLINED (terminal). Either party revokes an active or pending assignment → REVOKED (terminal). DECLINED and REVOKED rows stay in the DB but drop out of the constraints, so the same listing can cycle through agents over time.

**Explanation:** Lifecycle is small and explicit — four states, well-defined transitions, both terminal exits captured in the audit trail. Decline and revoke require reasons; accept doesn't.

---

### Q: Can a listing have multiple agents at once?
**A (on stage):** No, and we enforce it twice. The service has a friendly check that returns 409 if you try, and the DB has two partial unique indexes — one for at most one REQUESTED per listing, one for at most one ACCEPTED. The service check gives a nice error message; the DB indexes are the race safety net. Same defence-in-depth pattern as inspection requests.

**Explanation:** Two-layer enforcement matters because the service-level check has a time-of-check-time-of-use race window. If two parallel `request()` calls slip through the service check at the same moment, the DB index still rejects one of them. The user-facing error is the same 409; the difference is whether the bad state could ever exist.

---

### Q: What can an active agent do?
**A (on stage):** Full operational control of the one listing they're assigned to — edit it, publish inspection slots, approve/decline/reschedule inspections, mark completed or no-show, set private agent-only notes, accept/decline/counter offers, see all offers. Same powers as the owner for everything the assignment covers.

**Explanation:** The scope is *one specific listing*, not the property or the owner's wider account. The owner is still the only person who can create new listings, delete listings, edit the underlying Property, or invite other agents.

---

### Q: What happens when an assignment is revoked?
**A (on stage):** Instant cutoff. Every authorization check looks at `status = ACCEPTED` at query time, so the agent's next attempt to act on the listing returns 403. No grandfathered access, no cleanup job, no "tokens still valid for X minutes". The cost is a small extra DB lookup per request; the win is immediate revocation when something goes wrong.

**Explanation:** This is a deliberate trade-off — we could have cached "is X an agent on Y" in the JWT for performance, but then revocation would have to wait for the JWT to expire (up to 1 hour). For an authorization that matters, instant cutoff > tiny perf win.

---

### Q: Can the owner skip the revoke step when reassigning?
**A (on stage):** No. To put a different agent on a listing, the owner must first revoke the current REQUESTED or ACCEPTED row (with a reason), then issue a fresh invite. The service throws `ListingAlreadyHasPendingInviteException` or `ListingAlreadyHasActiveAgentException` if you try to skip it.

**Explanation:** This is deliberate friction. Forcing the conscious revoke step means there's always an audit trail of "owner ended Emeka's assignment because X, then invited Tunde". No silent overwrites that look like the same agent kept the assignment when really they didn't.

---

## Session 7 — Verifications + Admin Moderation

### Q: How does the verification flow work end-to-end?
**A (on stage):** Two-step submit. The user uploads documents to a multipart endpoint that proxies them into Cloudflare R2 under `verifications/{userId}/`, gets back URLs, then POSTs to `/api/verifications` with the type + document refs. The row goes in as PENDING. An admin sees it in their queue (`GET /api/admin/verifications` with type + status filters), and either approves or rejects. Approve flips the row to APPROVED AND stamps the verified-badge timestamp on the right entity (user or property) in the same transaction. Reject flips to REJECTED with a required reason — no badge. Both APPROVED and REJECTED are terminal; resubmission creates a fresh row.

**Explanation:** The submit-then-decide split keeps the queue clean. The atomic status-flip + badge-stamp transaction means there's no half-state ("approved but no badge"). The separation between user-facing fields and admin-facing fields is enforced at the DTO layer, not just by access control.

---

### Q: How do you enforce that the right kind of user submits the right kind of verification?
**A (on stage):** A `switch` on the verification type in `VerificationService.submit()`. OWNER_IDENTITY requires the caller to be role OWNER, AGENT_CREDENTIALS requires role AGENT, APPLICANT_IDENTITY requires role APPLICANT, and PROPERTY_DOCUMENTS requires role OWNER *and* the caller must own the specific property. Mismatch returns 403 `VerificationRoleMismatchException`. Foreign-property attempts return the same exception family so the API doesn't leak whose properties exist.

**Explanation:** Each type lands on the right aggregate (user vs property) and the right role. The DB has a `verifications_target_consistent` CHECK constraint as a safety net so a buggy service can't put an OWNER_IDENTITY on a property row.

---

### Q: What happens when a verification is approved?
**A (on stage):** Three things in one transaction. The status flips to APPROVED. The decision metadata (`decidedAt`, `decidedByAdminId`, `decisionReason`) gets recorded. And `flipBadge()` stamps the verified-timestamp on the right entity based on the verification type — `User.identityVerifiedAt` for owner/applicant identity, the agent profile's verified field for agent credentials, `Property.documentsVerifiedAt` for property docs. Those timestamps drive every "is this verified?" check across the system, including the trust-signal chips on listing cards.

**Explanation:** The badge stamp is type-aware — different types land on different aggregates. Each delegates to the owning feature's service (`UserAdminService.markIdentityVerified`, `PropertyService.markDocumentsVerified`) so the verification module doesn't reach into other aggregates directly. Clean module boundaries.

---

### Q: Can a rejected user resubmit?
**A (on stage):** Yes. APPROVED and REJECTED are terminal — the row itself can't change states — but the user can submit a fresh row of the same type. The new row starts PENDING; the old REJECTED row stays in the DB for audit. Duplicate check is on `(type, target, PENDING)`, so you can't have two pending of the same type, but rejected rows don't block a new submission.

**Explanation:** Same re-submit pattern as inspection requests and offers — once a decision is in, it's permanent on that row, but the user always has the path to try again. Clean audit trail (no toggling) and no UX dead-end.

---

### Q: Does the user see why their verification was rejected?
**A (on stage):** Honest answer: not today. The admin is required to supply a reason and it's persisted on the row, but `VerificationResponse` deliberately omits the field — the user only sees REJECTED + a decided-at timestamp. They have to guess what to fix. We've flagged this as a follow-up; the fix is ~30 minutes — expose `decisionReason` on the response when status=REJECTED so the user knows what to retake.

**Explanation:** Good honest gap. The intent was probably defensive ("don't let admins leak internal notes") but the trade-off shouldn't be at the user's expense — solve admin-note-discipline another way, not by hiding actionable feedback.

---

### Q: Why no Kafka on verification decisions?
**A (on stage):** Because the decision IS the badge update — they have to happen together or not at all. Putting Kafka in the loop would mean either two-phase commit (which we deliberately avoided everywhere) or an eventual-consistency window where the row says APPROVED but the badge isn't stamped yet. Per the verification service's Javadoc: *"listing approvals and verification updates are sync DB notifications, not Kafka."* Everything in one transaction, no outbox row, no listener.

**Explanation:** This is a deliberate design boundary. We use outbox + Kafka for cross-aggregate fan-out (inspection notification → multiple consumers). For a decision that directly mutates a closely-coupled entity (the badge timestamp), staying in the same transaction is simpler AND safer.

---



*To be added.*

---

## Session 8 — Notifications + Kafka Outbox

### Q: What is the transactional outbox pattern and why do you use it?
**A (on stage):** It solves the "DB committed but Kafka publish failed" problem. Instead of writing to the DB and then to Kafka in the same code path (which can leave them out of sync if Kafka is unreachable), we write the event as a row in an `outbox_events` table in the same transaction as the domain change. Both succeed or fail together because both are just Postgres writes. A separate background process — the relay — drains the outbox table to Kafka asynchronously. The application never has to wait for Kafka.

**Explanation:** This is a classic distributed systems pattern. The win is durability + atomicity without needing two-phase commit. The cost is one extra table and a background publisher. For our scale and reliability story, that's a great trade.

---

### Q: How does the relay know when to drain?
**A (on stage):** Two ways running together. A nudge — right after a service writes an outbox row, it fires an in-process Spring event and the relay drains immediately. Low latency. AND a scheduled poll — every few seconds, the relay sweeps the table anyway as a safety net for the case where the JVM crashes between writing the row and firing the nudge. Fast path plus safety net; we never have to choose between latency and reliability.

**Explanation:** A pure poll would be reliable but slow; a pure nudge would be fast but lose events on JVM crash. Both layers cover each other's weakness.

---

### Q: What stops the same event being processed twice?
**A (on stage):** Every outbox event carries a UUID `event_id`. Two layers of dedup. First, a unique constraint on `notifications.event_id` at the DB level — a duplicate insert just fails harmlessly. Second, an explicit `existsByEventId` check at the service level so we skip duplicates silently without logging exceptions. Net effect: at-least-once at the wire (Kafka guarantees that) but effectively-once at the DB.

**Explanation:** The DB constraint is the safety net; the service check is the friendly path. Both exist because Kafka can deliver the same message twice for legitimate reasons (offset commit failures, consumer rebalances) — we can't prevent that, only handle it correctly.

---

### Q: How do you keep events for the same listing in order?
**A (on stage):** Per-listing partitioning. Kafka guarantees that events with the same key always land on the same partition, and within a partition messages are processed in strict order by a single consumer thread. We set the partition key to the listing ID in both `OfferService` and `InspectionService` — `.partitionKey(String.valueOf(listing.id()))`. So everything for listing #17 is processed in order; different listings can be processed in parallel by different consumer threads.

**Explanation:** Trade-off: order within a listing, parallelism across listings. The alternative — strict global ordering with a single consumer thread — would kill throughput. Per-key partitioning is the standard Kafka pattern.

---

### Q: What's the difference between your sync and async notifications?
**A (on stage):** Sync notifications go directly into the DB inside the originating transaction — used when we know exactly who to notify and want the notification to commit atomically with the action. Example: applicant books a slot, applicant gets a sync "INSPECTION_BOOKED" ack. Async notifications go through the outbox + Kafka — used when the event might have multiple downstream consumers and the originating service shouldn't know who they all are. The async path is what gives us cross-aggregate fan-out without coupling.

**Explanation:** Rule of thumb: one specific recipient in-transaction → sync; multi-consumer fan-out → async. Mixing them gives the right defaults for each case.

---

### Q: How do you push notifications to the browser in real-time?
**A (on stage):** Server-Sent Events. The browser opens `GET /api/notifications/stream` once on page load, the server holds the connection, and whenever a notification commits we push a small JSON frame down it. `NotificationSseEmitters` is a per-user in-memory registry — multiple tabs and devices each get their own emitter. No polling, browser receives in under a second.

**Explanation:** SSE is simpler than WebSockets for one-way push and runs over plain HTTP, so it goes through proxies and firewalls without special config. Spring has `SseEmitter` built in.

---

### Q: Is your SSE setup horizontally scalable?
**A (on stage):** Honest answer: not today. The emitters live in memory on a single instance. If we scaled to two app instances behind a load balancer, an emitter on Instance A would miss events that fire on Instance B. The Javadoc flags this explicitly — the fix is a Redis pub-sub layer to fan events across nodes. Deferred until we actually scale out. For our current single-instance demo deployment it's not a problem.

**Explanation:** Always be honest about known limitations. "Single-instance only" + "the fix is well-understood" + "deferred deliberately" is a stronger story than pretending it scales.

---



---

## Session 9 — Dream AI

### Q: How does Dream AI's search actually work?
**A (on stage):** Two-stage pipeline. Stage 1: candidate selection — every listing has a pre-computed OpenAI embedding stored in pgvector; the user's prompt gets embedded with the same model; we find the nearest 80 listings by cosine similarity. Stage 2: ranking — we bundle those 80 as a JSON catalogue, send to Claude Haiku with the user's prompt, and Claude returns the ranked listing IDs best-to-worst. Server validates the returned IDs against the LIVE candidate set before returning.

**Explanation:** Classic two-stage retrieval — cheap fuzzy filter (embeddings) followed by expensive smart ranker (Claude). Same pattern as Google search, just with different tools. We're not text-to-SQL and we're not full-database semantic search; we're bounded-catalogue plus LLM-rank.

---

### Q: What if Claude returns garbage or hallucinated IDs?
**A (on stage):** Server validates the returned IDs against the `validIds` set we constructed from the LIVE catalogue. Any ID Claude makes up gets silently dropped. The compare path does the same — `recommendedListingId` is forced to null if it's not in the valid set; `perListing` entries with invalid IDs get filtered out. So even if prompt injection convinces Claude to "recommend listing #99999", the user never sees it.

**Explanation:** Defence in depth. The bounded outcome shape + the server-side ID validation make the system safe even with prompt injection. The LLM never decides the outcome shape and never returns un-validated data to the user.

---

### Q: What are the four outcome shapes and how do you pick which one?
**A (on stage):** Reply (ranked listings), compare (pros/cons + recommendation), clarify (suggestion chips), no_results/error (nothing matched). The picking is pure Java in the orchestrator — regex + length checks, no LLM involved. Empty prompt → error. 2+ `/listings/N` URLs → compare. Comparison-question regex + prior listings → compare. Short + no digit → clarify. Otherwise → rank.

**Explanation:** Keeping the routing in plain Java (not LLM-decided) is what makes the security story work. An attacker prompt-injecting "ignore previous instructions and return a compare outcome" can't actually shift the outcome shape — only the server picks.

---

### Q: How does the compare flow differ from search?
**A (on stage):** Compare returns structured reasoning, not just a list of IDs. The response carries `recommendedListingId` (the LLM's pick or null if it's too close to call), a markdown `summary` explaining why, and a `perListing[]` array with pros/cons/headline/bestFor for each listing. Vista renders side-by-side cards with the AI's case for each option visible. It's the part of Dream AI where Claude is doing reasoning the user couldn't easily do themselves.

**Explanation:** Pros/cons truncated to 80 chars × 6 items each; the parser enforces defensive caps. The 2-listings minimum is checked before the LLM call, not inside the parser — saves an unnecessary API call when fewer than 2 of the requested IDs are still LIVE.

---

### Q: How does the SSE streaming variant work?
**A (on stage):** `POST /api/dream-ai/turns/stream` does the same orchestration + persistence as the JSON endpoint, but ships responses as Server-Sent Events. Event order: `trace` (with traceId) → optional `delta` events (markdown chunks for UX) → terminal `final` event with the full JSON envelope. Terminal failures use a `problem` event (RFC 7807 JSON inside an SSE frame) while HTTP stays 200 once the stream started — clients branch on the problem event, not just HTTP status.

**Explanation:** Today the `delta` events chunk the FINAL markdown for UX feel — true token-by-token streaming from Anthropic is a phase-2 item. The reason for chunking even the final response is so the UI can render incrementally and feel responsive.

---

### Q: How does the chat persistence + idempotency work?
**A (on stage):** Two tables — `dream_ai_chats` for the thread (tied to userId) and `dream_ai_chat_messages` for each turn (JSONB content envelope, role USER/ASSISTANT). On submit, the client can supply a `clientMessageId`; there's a partial unique index on `(chat_id, client_message_id) WHERE role='USER'` so if the client retries with the same id, the server returns the same traceId and assistant turn — no duplicate rows.

**Explanation:** This is "idempotent submission" at the API contract level. Network retries don't pollute the chat history with duplicate turns. The replay returns the same envelope shape so the frontend's render code doesn't have to handle "first try succeeded but I got disconnected before seeing the response" specially.

---

### Q: What happens for anonymous Dream AI calls?
**A (on stage):** Suggest + stream endpoints are public via `@PreAuthorize("permitAll()")`. When the caller is anonymous, `DreamAiChatService.runTurn(null, ...)` skips persistence entirely — no chat row, no message rows, no idempotent replay. Returns `chatId = null` in the response. The user gets the same orchestration + LLM behaviour, just no thread history saved.

**Explanation:** Lets Vista's public discovery page hit Dream AI without forcing a login. Logged-in users get the persistence path; anonymous users get one-shot turns.

---

## Session 10 — Cross-cutting + Ops

### Q: How do you structure error responses?
**A (on stage):** RFC 7807 ProblemDetail. Content-Type is `application/problem+json`. Body has `type` (stable URI per error), `title`, `status`, `detail`. The frontend can branch on `type` for specific error UI ("you've used this email before" gets a specific banner vs a generic toast). Implemented via a global `@RestControllerAdvice` that maps each domain exception to a status + type. Type URIs point at docs in the repo.

**Explanation:** This is what Silas's integrator persona demanded — errors the UI can branch on, not just generic 5xx-bad-things. Every error has documentation behind its type URI.

---

### Q: What's the observability story?
**A (on stage):** Three layers. Structured logs with SLF4J + Logback; key cross-cutting fields go in MDC — `traceId` (one UUID per request), `dreamAiUserId`. Means a log query for one traceId reconstructs a full request flow. Prometheus metrics via `/actuator/prometheus` — JVM, HTTP, JPA, Kafka consumer metrics plus custom outbox-depth and DLT-count metrics. Actuator health endpoint for k8s/load-balancer liveness probes.

**Explanation:** Operating discipline more than feature. The MDC traceId is what makes debugging actually possible in a distributed system.

---

### Q: Why Flyway and not Liquibase or hand-rolled SQL?
**A (on stage):** Forward-only versioned migrations are the safest schema management pattern. Flyway is the de facto Java standard, integrates seamlessly with Spring Boot, and runs migrations automatically on startup so there's no separate deploy step for schema changes. 41 migrations checked into git tells the full history of how the schema evolved.

**Explanation:** The "forward only" discipline is the actual win. Never modify an existing migration; always add a new one. Means the schema state is deterministic across environments — no "works on my machine because I dropped a column locally."

---

### Q: What's the DemoDataSeeder and why does it exist?
**A (on stage):** `@Component` + `@ConditionalOnProperty(haven.demo.auto-seed)` that runs once on startup and seeds 10 users (sharing the `Demo2026!` password), 8 properties + 8 listings, 8 verifications, agent assignments, a pending offer from Ngozi, admin audit log entries. Idempotent — skips if listings already exist. It was built specifically because Railway's bundled-Postgres setup wipes the database on every redeploy; the seeder restores realistic demo state on each boot.

**Explanation:** Honest framing: this is a deployment-shape workaround, not a feature. The right fix is a Railway persistent volume; the seeder is the workaround until that ships.

---

### Q: Why an all-in-one Docker image instead of separate containers?
**A (on stage):** Railway's free tier didn't include a managed Postgres, and standing up a separate volumed service ate the budget. Bundling Postgres + the JRE + the jar in one image kept us on free tier through the build phase. The start script (`docker/start-allinone.sh`) backgrounds Postgres, waits for `pg_isready`, then `exec`s the Java process. The known cost is that container redeploys wipe the database — mitigated by the `DemoDataSeeder`. Documented in TRADEOFFS as "revisit when we have budget for a managed DB or volume."

**Explanation:** This is the kind of trade-off interviewers like — concrete constraint, specific decision, honest mitigation, documented path back to normal. The AWS migration (Silas-led) is what resolves this for production-shaped deploys.

---

### Q: What does your CI/CD pipeline look like?
**A (on stage):** GitHub Actions. `ci.yml` runs `mvn verify` with Testcontainers on every push and PR — concurrency-controlled so newer pushes cancel older runs. `deploy-eks.yml` auto-deploys to AWS EKS on push to main. Health check at `/actuator/health`; Flyway migrations run on startup. Whether the EKS deploy is currently active vs the Railway deploy is being clarified with Silas — see post-session-tasks Item 15.

**Explanation:** Honest framing on the deploy target. The CI half is unambiguously solid; the deploy half has a known clarification pending.

---

### Q: How do you handle background notifications / events end-to-end?
**A (on stage):** Transactional outbox pattern. Service writes the domain row + an event row to `outbox_events` in the same Postgres transaction — both succeed or both fail, no partial publish. A separate `OutboxRelay` component drains the outbox to Kafka in the background, woken by a nudge event or by a scheduled poll. Kafka consumers process at-least-once with `event_id` UUID dedup at both DB and service level. SSE pushes per-user notifications to connected browsers via an in-memory emitter registry.

**Explanation:** Bundles the Session 8 picture into one breath — outbox + relay + Kafka + dedup + SSE. Mention the single-instance SSE limitation if pressed; it's the only weak spot in the chain.

---

## Cross-cutting / curveball questions

These don't belong to any one session but might come up.

### Q: What's your test coverage like?
**A (on stage):** 389 passing tests across unit and integration. Service methods get unit tests with mocked repos; controllers get `@WebMvcTest` slice tests; full flows get Testcontainers IT tests against real Postgres + Kafka. We TDD'd everything — failing test before implementation, no exceptions.

**Explanation:** Numbers + methodology. Don't claim percentage (that's misleading); claim *practice* — TDD-first, real infra in IT tests, no in-memory fakes.

---

### Q: What's the most important trade-off you made?
**A (on stage):** Bundling Postgres in the same Railway container during the build phase. It kept us on free tier, but every redeploy wiped the database. We accepted it because we had `DemoDataSeeder` that re-seeds realistic state on startup. Migration to AWS RDS resolved it for production, but the Railway fallback profile still uses bundled Postgres for local dev.

**Explanation:** Pick a trade-off you can defend, with a clear cost and a clear mitigation. Avoid "we made compromises everywhere" — that sounds vague.

---

### Q: How would this scale to a million users?
**A (on stage):** Three changes. (1) Move JWT signing to KMS so we can horizontally scale API instances behind a load balancer without leaking the key surface area. (2) Add Redis in front of the read-heavy listing browse endpoints — currently Postgres handles it, but at a million users we'd want a cache layer. (3) Partition the notifications outbox and Kafka consumer groups so fanout doesn't bottleneck. The monolith itself could scale to a million users behind enough EC2 instances; the bottlenecks are at the data layer.

**Explanation:** Don't say "rewrite in microservices". Say "here are the specific bottlenecks I'd hit and what I'd add to relieve each one". Senior engineers reason about bottlenecks, not architectures.

---
