# `not-found` — 404

The resource you asked for either never existed or is no longer visible to you.

## When this fires

- `GET /listings/{id}` for a listing that was deleted or administratively taken down.
- `GET /users/{id}/profile` for a user ID that doesn't exist.
- Any write endpoint targeting a foreign-key reference that doesn't exist
  (e.g. `POST /properties` where the inferred ownerId points at a deleted user).
- Any endpoint where the path parameter resolves to a soft-deleted row that's
  hidden from your access scope.

## Recovery

- **Don't retry.** The resource is gone — repeating the request won't change that.
- Surface the failure to the user as a navigation-level message ("Listing no
  longer available"), not a transient toast.
- If the resource was a listing the user had saved, consider unsaving it on the
  client side so the broken reference doesn't persist.

## Distinguishing 404 from 403

A `forbidden` (403) means the resource exists but the caller can't see it.
A `not-found` (404) means it either doesn't exist or has been taken down.
DreamHomes returns 404 for *both* "doesn't exist" and "soft-deleted from public
view" — we don't leak existence of admin-taken-down content via 403 vs 404.
