# `validation-failed` — 400

The request body or query parameters failed `jakarta.validation` constraints
declared on the request DTO. The response includes an `errors` array describing
every field that failed.

## Response shape extension

400 responses include an `errors` array on top of the standard `ProblemDetail`
fields:

```json
{
  "type": "https://github.com/DreamHom/haven/blob/main/docs/errors/validation-failed.md",
  "title": "Bad Request",
  "status": 400,
  "detail": "validation failed",
  "instance": "/api/auth/register",
  "errors": [
    { "field": "email",    "message": "must be a well-formed email" },
    { "field": "password", "message": "must contain at least one digit" }
  ]
}
```

## When this fires

- Missing required field on a `@RequestBody`.
- Format constraints (`@Email`, `@Pattern`, `@Size(min=...)`).
- DreamHomes-specific custom constraints:
  - `@StrictEmail` — stricter than RFC 5322; rejects `+`-addressing tricks
    on the registration path.
  - `@NotCommonPassword` — rejects passwords on the bundled common-passwords
    blocklist.

## Recovery

- Don't retry until the failing fields are fixed.
- The `errors[]` array gives field-level granularity — surface each one
  inline in the form.
- Server-side validation is the source of truth; never assume your client-side
  validation caught everything.
