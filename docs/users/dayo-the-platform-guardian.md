# Dayo — The Platform Guardian

> *"Every badge I approve is a promise to the person who sees it."*

## Profile

| | |
|---|---|
| **Role** | Admin (DreamHomes trust & safety team) |
| **Age** | (internal — irrelevant) |
| **Location** | Internal (DreamHomes office / remote) |
| **Background** | Trust & safety operator. Not a property professional — an integrity professional. |

## The story

Dayo's job is to make sure that every verified badge means something. He
reviews document submissions carefully — a C of O that doesn't match the
address on the listing gets rejected with a detailed reason, not just a
generic decline. He monitors the verification queue every morning, checks the
audit log before approving anything sensitive, and takes down listings that
have been reported twice without waiting for a third. He suspended three
agent accounts last month for taking fees off-platform. He doesn't see
himself as a gatekeeper — he sees himself as the reason people like Ngozi
can trust the platform.

## What he cares about

- **Queue visibility.** He wants to see what needs his attention right now.
- **Detailed rejection reasons.** A generic "rejected" is useless to the
  submitter. The system must let him write a real explanation.
- **An audit trail.** Every action he takes — approve, reject, suspend,
  takedown — must be logged with who/when/why.
- **Reversibility.** A user he suspended in error must be reactivatable.
- **Sensitive action confirmation.** Before suspending an agent, he reads
  the audit log to make sure he's not about to revoke a badge from someone
  who flagged something legitimate.

## User stories

### Story 1 — See the verification queue ✅ Implemented

**As an** admin
**I want to** see all pending verification submissions
**So that** I can prioritise the morning queue.

**Acceptance criteria**
- [x] `GET /admin/verifications` returns paginated PENDING verifications.
- [x] Filterable by type (`OWNER_IDENTITY`, `APPLICANT_IDENTITY`, `AGENT_CREDENTIALS`, `PROPERTY_DOCUMENTS`).
- [x] Each entry shows submitter ID, target user / property, submitted-at, and the document refs.
- [x] Returns 403 for non-admin callers.

**Endpoints involved**
- `GET /admin/verifications` *(confirm exact path in inventory)*

---

### Story 2 — Approve a verification ✅ Implemented

**As an** admin
**I want to** approve a submission with optional notes
**So that** the user's profile / property gets the appropriate verified badge.

**Acceptance criteria**
- [x] `POST /admin/verifications/{id}/approve` records `decided_by_admin_id`, `decided_at`, optional `decision_reason`.
- [x] On approval:
  - `OWNER_IDENTITY` / `APPLICANT_IDENTITY` → stamps `users.identity_verified_at`.
  - `AGENT_CREDENTIALS` → stamps `agent_profiles.credential_verified_at`.
  - `PROPERTY_DOCUMENTS` → stamps `properties.documents_verified_at`.
- [x] Submitter gets `VERIFICATION_APPROVED` notification.
- [x] Audit log row written with action=`VERIFICATION_APPROVED`.
- [x] Cannot approve a non-PENDING submission (409).

**Endpoints involved**
- `POST /admin/verifications/{id}/approve`

---

### Story 3 — Reject a verification with a real reason ✅ Implemented

**As an** admin
**I want** rejection to require a non-empty reason
**So that** submitters know exactly what to fix.

**Acceptance criteria**
- [x] `POST /admin/verifications/{id}/reject` requires `reason` field (validated min length).
- [x] Rejection reason stored, surfaced in submitter's notification, visible in their `GET /verifications/mine`.
- [x] Empty / whitespace-only reason → 400 before any DB write (early guard).
- [x] Audit log row written.

**Endpoints involved**
- `POST /admin/verifications/{id}/reject`

---

### Story 4 — Suspend a user account ✅ Implemented

**As an** admin
**I want to** suspend a user with a reason
**So that** they can no longer act on the platform until reviewed.

**Acceptance criteria**
- [x] `POST /admin/users/{id}/suspend` stamps `users.suspended_at` and bumps `tokenVersion`.
- [x] All outstanding JWTs for that user become invalid on the next request (token version mismatch → 401).
- [x] Audit log row written with reason.
- [x] Cannot suspend an already-suspended user (409).
- [x] Cannot suspend yourself (defensive — `CannotModerateSelfException` → 403).

**Endpoints involved**
- `POST /admin/users/{id}/suspend`

---

### Story 5 — Reactivate a suspended user ✅ Implemented

**As an** admin
**I want to** reactivate a wrongly-suspended user
**So that** the suspension is reversible.

**Acceptance criteria**
- [x] `POST /admin/users/{id}/reactivate` clears `suspended_at`.
- [x] Token version is NOT bumped again (the suspend bump already invalidated everything; double-bumping would force an unnecessary re-login).
- [x] Audit log row written.
- [x] Cannot reactivate a non-suspended user (409).

**Endpoints involved**
- `POST /admin/users/{id}/reactivate`

---

### Story 6 — Take down a listing ✅ Implemented

**As an** admin
**I want to** take down a listing for policy violation
**So that** it no longer appears in public discovery.

**Acceptance criteria**
- [x] `POST /admin/listings/{id}/takedown` stamps the listing as `TAKEN_DOWN` (or sets `deleted_at` — *confirm*).
- [x] Public `GET /listings` excludes it.
- [x] `GET /listings/{id}` returns 404 / 410 *(confirm)*.
- [x] Audit log row written with reason.
- [x] Owner gets a notification.

**Endpoints involved**
- `POST /admin/listings/{id}/takedown`

---

### Story 7 — Read the audit log ✅ Implemented

**As an** admin
**I want to** see every admin action ever taken, by whom, with reason, target, timestamp
**So that** I can self-audit before approving anything sensitive.

**Acceptance criteria**
- [x] Audit log entries written by **every** admin write — verifications, suspensions, takedowns.
- [x] Read endpoint returns paginated entries with filters by action type, target type, target id, actor id, date range.
- [x] Read endpoint requires admin role (403 otherwise).

**Endpoints involved**
- `GET /admin/audit-logs` *(confirm exact path in inventory)*

---

### Story 8 — User-facing report flow feeding the moderation queue ⬜ Future

**As an** admin
**I want to** see a queue of user-reported listings (Ngozi flags a scam → it shows up here)
**So that** I'm not just reactively taking things down — I'm responding to community signals.

**Status**: No `POST /listings/{id}/report` endpoint exists; admin moderation is currently top-down only. Worth adding when scaling trust & safety operations.

---

## Journey through the platform

Dayo's chronological flow on a typical morning:

1. **Open the verification queue** → `GET /admin/verifications?status=PENDING`.
2. **For each pending submission**:
   a. Read submitter context — `GET /users/{submitter_id}/profile`.
   b. Read document refs.
   c. APPROVE if everything checks out → `POST /admin/verifications/{id}/approve`.
   d. REJECT with detailed reason if anything mismatches → `POST /admin/verifications/{id}/reject`.
3. **Periodic audit log scrub** → `GET /admin/audit-logs?actor_id=me` to spot mistakes.
4. **Investigate flagged accounts** → `GET /users/{id}/profile` + `GET /admin/audit-logs?target_user_id={id}`.
5. **If warranted, suspend** → `POST /admin/users/{id}/suspend` with reason.
6. **If a listing is escalated** → review + `POST /admin/listings/{id}/takedown` with reason.

## Possible errors he encounters

| Scenario | HTTP | Body | UI guidance |
|---|---|---|---|
| Approving an already-approved verification | `409` | `... "detail":"verification already decided"` | "This was already decided." |
| Rejecting with empty reason | `400` | Validation error on `reason` field | Inline form error. |
| Suspending himself | `403` | `... "detail":"cannot moderate self"` | (Defensive — UI shouldn't expose.) |
| Suspending an already-suspended user | `409` | `... "detail":"user already suspended"` | "User is already suspended." |
| Reactivating a non-suspended user | `409` | `... "detail":"user is not suspended"` | "User is not suspended." |
| Action by non-admin | `403` | Standard | (Defensive — non-admin tokens never reach admin endpoints.) |
| Approving a verification whose target user / property doesn't exist (race) | `404` | `... "detail":"target not found"` *(confirm in inventory)* | "Target was deleted while you were reviewing." |

## Test scenarios

### Golden path: morning queue

```
1. Setup: 3 pending verifications (1 OWNER_IDENTITY, 1 AGENT_CREDENTIALS, 1 PROPERTY_DOCUMENTS)
2. As Dayo, GET /admin/verifications?status=PENDING → assert all 3 returned
3. Approve OWNER_IDENTITY → assert badge stamped on user, notification sent, audit log written
4. Approve AGENT_CREDENTIALS → assert badge stamped on agent profile, notification, audit log
5. Reject PROPERTY_DOCUMENTS with reason "address mismatch on C of O" → assert REJECTED, reason stored, notification carries reason, audit log written
6. GET /admin/verifications?status=PENDING → assert empty
```

### Suspension lifecycle

```
1. Setup: Agent Emeka has an active JWT
2. As Dayo, POST /admin/users/{emeka.id}/suspend reason="took fees off-platform" → 200, audit log
3. Emeka attempts any authenticated request with his old JWT → 401 (token version stale)
4. Emeka tries to log in → 403 (account suspended)
5. As Dayo, POST /admin/users/{emeka.id}/reactivate → 200, audit log
6. Emeka logs in fresh → 200, JWT issued, can act normally
```

### Audit-log integrity

- Every admin write produces exactly one audit log row (no duplicates, no misses).
- Audit log read returns chronological order.
- Audit log row includes: actor_id, action, target_type, target_id, reason, timestamp.
- Soft-deleted records: audit log entries are NEVER deleted.

### Defensive

- Dayo cannot suspend himself → 403.
- Non-admin user with hand-crafted request to `/admin/...` endpoints → 403.

## Related personas

- **[Ngozi](ngozi-the-skeptic.md)** is the user whose trust depends on Dayo
  doing this job rigorously.
- **[Amaka](amaka-the-lagos-landlord.md)** + **[Biodun](biodun-the-developer.md)**
  + **[Emeka](emeka-the-hustling-agent.md)** + **[Temi](temi-the-first-timer.md)**
  all submit verifications that pass through Dayo's queue.
- (Future) Reporters of scam listings would feed Dayo's moderation queue.

## Dayo as a meta-persona

Dayo is also the persona who tells you what's broken in this list. If
auditing rules don't fire, if rejection reasons are weak, if suspension
isn't reversible — Dayo is the canary. Test for Dayo's flow rigorously and
the trust signals every other persona depends on stay credible.
