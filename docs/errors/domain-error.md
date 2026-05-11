# `domain-error` — fallback type for `DomainException` on uncatalogued statuses

Catch-all type URI used when a `DomainException` subclass returns a status
that isn't explicitly mapped in `GlobalExceptionHandler.typeFor()` (today:
anything other than 400, 401, 403, 404, 409, 429).

## When this fires

In practice — almost never. Every `DomainException` subclass currently
declares one of the explicitly-mapped statuses. This URI exists so that if
someone introduces a new status (e.g. 422 for a future semantic-validation
case), the response still carries a non-`about:blank` type while we wait
for the real slug to be added.

## Action item if you see this in production

The presence of `domain-error` in a real response is a signal that
`GlobalExceptionHandler.typeFor()` needs a new explicit branch. Open an
issue, add the slug, and create a sibling `<slug>.md` in this directory.
The existing fallback prevents the response from being unhelpful while the
fix is in flight.
