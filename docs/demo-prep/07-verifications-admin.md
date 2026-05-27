# Session 7 — Verifications + Admin Moderation

## What verification is for

Verification is how a user (owner, agent, applicant) or a property earns a trust badge that shows up publicly. Ngozi the skeptic won't engage with strangers until she sees a badge — verification is the system's primary trust signal.

## The 4 verification types

| Type | Who/what is verified | Target column |
|---|---|---|
| OWNER_IDENTITY | An owner user | `target_user_id` |
| AGENT_CREDENTIALS | An agent user | `target_user_id` |
| APPLICANT_IDENTITY | An applicant user | `target_user_id` |
| PROPERTY_DOCUMENTS | A specific property | `target_property_id` |

A DB CHECK constraint (`verifications_target_consistent`) enforces that the target ID matches the right table per type — you can't accidentally point an OWNER_IDENTITY verification at a property row.

## The 3-status lifecycle

Three states:

- **PENDING** — submitted, waiting for admin review
- **APPROVED** — terminal; badge stamped
- **REJECTED** — terminal; submitter has to start over (new row)

PENDING is the only state that can transition. APPROVED and REJECTED are dead ends. Re-submitting after a rejection creates a fresh row — the rejected one stays for audit.

## The submit flow

Two steps from the user's side:

1. **Upload documents** — frontend POSTs multipart files to `/api/verifications/files`. Haven proxies them to R2 under `verifications/{userId}/`. Returns the public URL.
2. **Create the verification row** — frontend POSTs to `/api/verifications` with the verification type + `documentRefs` (URLs from step 1 + metadata like `{kind: C_OF_O}`). Persists a PENDING row.

Same proxied-upload trade-off as listing photos. Works fine at our scale; pre-signed URLs would be cleaner at volume (already on the task list).

## The role gate

`VerificationService.submit()` uses a `switch` on the verification type to enforce who can submit what:

| Type | Submitter must be |
|---|---|
| OWNER_IDENTITY | OWNER |
| AGENT_CREDENTIALS | AGENT |
| APPLICANT_IDENTITY | APPLICANT |
| PROPERTY_DOCUMENTS | OWNER (and must own the specific property) |

Mismatch → 403 `VerificationRoleMismatchException`. Trying to submit `PROPERTY_DOCUMENTS` for someone else's property → same exception family (so we don't leak "this property exists but isn't yours").

## One pending per type per target

You can't have two PENDING rows of the same type for the same target (`existsByTypeAndTargetUserIdAndStatus`). If you've submitted OWNER_IDENTITY and it's awaiting review, you can't submit another — `DuplicatePendingVerificationException` → 409.

But if a row is **rejected**, you can submit again. Fresh PENDING row, old REJECTED row stays for audit. Same re-submit pattern as inspection requests.

## The admin review flow

Admin uses a unified queue: `GET /api/admin/verifications` with optional `?type=` and `?status=` filters. Paginated, ordered by submission time (oldest first).

Two actions on a PENDING row:

- **Approve** — `POST /api/admin/verifications/{id}/approve`
- **Reject** — `POST /api/admin/verifications/{id}/reject` (reason required)

Both go through `loadPending()` first, which throws `VerificationAlreadyDecidedException` (409) if the row is already decided. No toggle bugs.

## What approval actually does (the badge stamp)

This is the elegant part. `VerificationAdminService.approve()` does **all of this in one transaction**:

1. Flip the verification row to APPROVED + record `decidedAt`, `decidedByAdminId`, `decisionReason`
2. Call `flipBadge(verification, now)` which stamps a timestamp on the right entity per type:

| Verification type | What gets stamped |
|---|---|
| OWNER_IDENTITY | `User.identityVerifiedAt` |
| APPLICANT_IDENTITY | `User.identityVerifiedAt` (same field) |
| AGENT_CREDENTIALS | the agent profile's verified field |
| PROPERTY_DOCUMENTS | `Property.documentsVerifiedAt` |

Those timestamps drive every "is this user/property verified?" check across the system — including the trust-signal chips on listings (Item 16).

If the status flip succeeds but the badge stamp fails (or vice versa), the whole transaction rolls back. No half-state.

## What rejection does

Simpler: status → REJECTED + `decidedAt` + `decidedByAdminId` + a **required** reason. No badge stamp. Terminal — user has to submit a fresh row to try again.

## The gap: decisionReason isn't shown to the submitter

The reason is captured (line 99-101 of `VerificationAdminService` throws if missing) and persisted (`decision_reason` column). But `VerificationResponse.java` deliberately omits the field. The Javadoc says: *"We don't expose `decisionReason` on submission responses."*

So Amaka submits her identity → admin rejects with "photo too blurry" → Amaka sees REJECTED but no reason → resubmits with the same photo → loop.

This is bad UX and tracked as Item 21 on the post-session task list. Fix is ~30 minutes: add the field to `VerificationResponse`, populate it when status=REJECTED.

## Not in the loop: Kafka

Per the class Javadoc: *"listing approvals and verification updates are sync DB notifications, not Kafka — this whole flow stays in one transaction with no outbox involvement."*

So verification decisions don't fire async events. The decision IS the user's badge update, and both happen atomically in one DB transaction. No outbox row, no Kafka listener, no eventual consistency window.
