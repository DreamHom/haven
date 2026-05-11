# `unauthenticated` — 401

The request has no JWT, an expired JWT, a JWT with an invalid signature, or a
JWT whose `tokenVersion` no longer matches the user's current `tokenVersion`
(meaning the token was revoked — typically by logout, password change, or
admin suspension).

## When this fires

- No `Authorization` header, on an endpoint that requires auth.
- `Authorization: Bearer <something>` where `<something>` doesn't parse, has
  the wrong signature, or has expired (`exp` claim < now).
- The token's `tv` (tokenVersion) claim doesn't match `users.token_version`.
  This happens after:
  - `POST /auth/logout` (own logout — bumps own tokenVersion).
  - `POST /admin/users/{id}/suspend` (admin revokes a user's sessions).

## Recovery

- **Drop any cached token state on the client.**
- Surface a "your session has expired, please log in again" message.
- Redirect to the login flow.
- After re-login, retry the original action.

## Don't confuse with 403

- 401: "I don't know who you are." → log in.
- 403: "I know who you are, but you can't do this." → don't retry.

A common bug is showing the same "permission denied" UI for both — they're
different actions for the user.
