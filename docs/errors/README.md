# DreamHomes Haven — Error reference

Every 4xx / 5xx response from this API is shaped as an [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807)
`ProblemDetail`:

```json
{
  "type": "https://github.com/DreamHom/haven/blob/main/docs/errors/<slug>.md",
  "title": "<HTTP reason phrase>",
  "status": <code>,
  "detail": "<human-readable explanation>",
  "instance": "<the request path that failed>"
}
```

The `type` URI is **stable** across versions and points to the file in this directory
that explains the family. Clients should branch on `type` rather than parsing `detail`.

> **Status of these docs**: today, `<slug>.md` files in this directory are the
> per-error reference. When a hosted docs site exists (e.g. `docs.dreamhomes.com`),
> override `HAVEN_ERRORS_TYPE_BASE` to point there and these markdown files become
> the source the hosted pages render from.

## Error catalog

| Slug | HTTP | When it fires | Recovery |
|---|---|---|---|
| [`validation-failed`](validation-failed.md) | 400 | Request body or query param failed `jakarta.validation` constraints | Fix the field in `errors[]`, retry |
| [`unauthenticated`](unauthenticated.md) | 401 | Missing JWT, expired JWT, JWT for a tokenVersion that's been bumped | Log the user back in; clear stored credentials |
| [`forbidden`](forbidden.md) | 403 | Authenticated but lacks the role / ownership / assignment the action requires | Don't retry — escalate to the user, don't surface as "try again" |
| [`not-found`](not-found.md) | 404 | Target resource does not exist (or has been administratively taken down / soft-deleted) | Don't retry; navigate elsewhere |
| [`conflict`](conflict.md) | 409 | Illegal state-machine transition, duplicate-row constraint, or optimistic-lock race | Refetch current state and decide whether to retry |
| [`rate-limited`](rate-limited.md) | 429 | Per-IP token bucket exhausted on `POST /auth/register` or `POST /auth/login` | Wait + retry with exponential backoff |
| [`domain-error`](domain-error.md) | other | Catch-all for `DomainException` subclasses on a status not in the table above | Status-specific |

## Conventions

- `type` URIs **never change**. Once published, a slug is permanent. New error
  families get new slugs; existing ones don't get renamed.
- `title` matches the HTTP reason phrase ("Not Found", "Conflict", etc.). Useful
  for logs; not for branching logic.
- `detail` is human-readable and may include resource IDs. **Don't show it
  unmodified to end users** — translate via your i18n layer.
- `instance` is the path that triggered the error. Useful for support tickets.
- Validation responses (400) include an `errors` array with `{ field, message }`
  entries describing every failed constraint.

## Adding a new error type

When introducing a new `DomainException` subclass that produces a status not in
the table:

1. Add a `case` to `GlobalExceptionHandler.typeFor()` mapping the status to a
   slug.
2. Create `docs/errors/<slug>.md` documenting when it fires and how to recover.
3. Add an entry to the table above.
4. Reference the new slug in the corresponding `OpenApiConfig` reusable response.
