# `forbidden` — 403

You're authenticated, but the action requires a role, ownership, or assignment
your account doesn't carry.

## When this fires

- An `APPLICANT` calls an endpoint gated `@PreAuthorize("hasRole('OWNER')")`,
  e.g. trying to create a listing.
- An `OWNER` calls a write on a listing they don't own
  (`PATCH /listings/{id}` for someone else's listing).
- An `AGENT` who hasn't been assigned to a listing tries to open inspection
  slots on it.
- A `Role.ADMIN` user tries to suspend themselves
  (`CannotModerateSelfException` — defensive guard).
- The caller's account is suspended (`users.suspended_at` is set) and they're
  trying to take any privileged action.

## Recovery

- **Don't retry.** Repeating the request won't change authorisation.
- Surface as a permanent error to the user, not a transient one.
- If the user is genuinely surprised they can't do this, the next step is
  product support, not technical retry.

## What 403 does NOT mean

- It does NOT mean "your token expired" — that's `unauthenticated` (401).
- It does NOT mean "the resource doesn't exist" — that's `not-found` (404).
- It does NOT mean "rate limit hit" — that's `rate-limited` (429).

DreamHomes follows the convention that 403 is reserved for "you can't do this
because of who you are or what role/ownership you hold." Existence-leaking
behaviour (returning 403 for resources that exist but the caller can't see) is
deliberately avoided — admin-taken-down content returns 404.
