# `rate-limited` — 429

You exceeded the per-IP token bucket on a rate-limited path. Currently this
applies to the auth-related endpoints only:

- `POST /auth/register`
- `POST /auth/login`

Other endpoints don't have a per-IP throttle today.

## Why this exists

These two endpoints are the bruteforce / credential-stuffing surface. The
in-process `bucket4j` filter caps how many attempts a single IP can make in
a short window. Legitimate users almost never hit it; scripts do.

## Recovery

- **Wait.** The bucket refills at a steady rate. Aggressive retries make it
  worse, not better.
- Use exponential backoff (start at ~1s, double each retry, cap at ~30s).
- If a real user is consistently hitting 429, something on the client is
  re-submitting the form on every keystroke or similar — fix the client,
  not the bucket.

## Notes

- The throttle is per-IP, not per-user, because the user identity isn't
  known yet on these endpoints.
- A shared NAT (corporate office, hotel WiFi) can collectively trip the
  limit. The window is short enough that this clears on its own; if it's a
  recurring problem, configure your client to back off harder.
