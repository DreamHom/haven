# `conflict` — 409

The request is well-formed and you're authorised, but the current state of the
resource prevents the operation from succeeding.

## When this fires

- **State-machine violation**: trying to ACCEPT an offer that's already CLOSED;
  trying to publish a listing that's been TAKEN_DOWN; trying to revoke an
  agent assignment that's already REVOKED.
- **Duplicate-row constraint**: two clients race to claim the same inspection
  slot — the slower one gets 409. Posting a duplicate review on the same
  `(listingId, reviewerUserId, revieweeUserId)` triple — same.
- **Optimistic lock contention**: two PATCH requests against the same
  `@Version`-locked entity hit the database in parallel — one wins, the other
  gets `ObjectOptimisticLockingFailureException` translated to 409 with the
  message "this resource was modified by someone else — reload and retry".

## Recovery

- **Refetch the resource first** to see what state it's actually in. The 409
  doesn't tell you the new state — it just tells you the assumption your
  request made is no longer true.
- After refetch, decide:
  - **State machine**: was the action still meaningful given the new state?
    Sometimes yes (re-decide from new context), sometimes no (the deal is done).
  - **Duplicate**: the second writer should usually treat the existing row as
    the canonical one (e.g. "already saved" → just show as saved).
  - **Optimistic lock**: re-apply the user's edit on top of the new state, ask
    them to confirm the merge if the conflict is non-trivial.
- Don't retry blindly in a loop — a stuck retry on a state-machine 409 will
  burn quota for no reason.
