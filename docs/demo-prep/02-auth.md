# Session 2 — Auth + Identity

## Stateless RS256 JWT

When a user logs in, the server hands them a JWT — a token containing their identity (user 12, role APPLICANT) plus a cryptographic signature.

**Stateless** means the server doesn't keep any record of who's logged in. The token itself is the proof. Every request, the server verifies the signature is genuine and trusts the claims inside.

This is unlike traditional sessions where the server keeps a lookup table ("session abc123 → user 7"). With JWTs there's no list — any server instance can verify any token without needing shared memory.

**RS256** is the signing algorithm. It uses a pair of keys:

- **Private key** — stays on the server, signs tokens
- **Public key** — could be published anywhere, verifies tokens

Only the private key needs to be guarded. The public key can sit on a billboard — it can verify signatures but can't forge them.

## What happens if the private key leaks

If the private key leaks, an attacker can mint a valid JWT as any user, including admin. Catastrophic.

What stops it being a permanent disaster: short access TTLs (1 hour) and a clean rotation story. Regenerate the keypair, restart the service, every old token becomes invalid, every user re-logs in. No lingering compromise.

What we'd add next: KMS-backed signing so the key physically can't be extracted even from a compromised host. That's industry standard for serious deployments; we haven't shipped it because it adds infra cost.

## Refresh tokens

The access JWT lives 1 hour. Short enough that a stolen token has a small damage window — but terrible UX if it meant users re-login every hour.

So at login the user gets **two** tokens:

- **Access JWT** — 1 hour, stateless, sent on every API call
- **Refresh token** — 30 days, stored as a DB row

When the access JWT expires, the frontend silently calls `POST /auth/refresh` with the refresh token, gets a new access JWT (and a new refresh token), and the user never notices.

Why are access tokens JWTs but refresh tokens DB rows? Different goals. Access tokens want fast verification (no DB lookup). Refresh tokens want revocability (delete the row to kill the session). Each used where it shines.

## Rotation + replay detection

Each time a refresh token is used, the old one is immediately revoked and a new one issued. So a refresh token is single-use.

If a revoked refresh token is ever presented again, the entire token chain is killed and the user has to log in fresh. This catches the silent-copy attack — if an attacker silently copies your refresh token, eventually one of you will present a revoked one and we'll know.

## Logging out a JWT

Stateless tokens are hard to revoke — once issued, they look valid until expiry. We solved this with two complementary mechanisms.

**jti blocklist** — a small table of revoked JWT IDs. When a user clicks "log out this device", we add the current token's `jti` to the table. The auth filter checks the blocklist on every request. Surgical — only kills the one token.

**tokenVersion bump** — every user has a `tokenVersion` integer in the DB. Tokens carry a `tv` claim. The filter compares them on every request. To kill **all** of a user's tokens at once (logout-everywhere, password change, admin suspend), we just increment the DB number. Every existing token now has a stale `tv` and dies.

Two intents → two mechanisms. Both run on every request.

## Anti-enumeration

A naive register endpoint leaks information: "email already taken" vs "201 Created" tells an attacker which emails are real accounts. Useful for phishing, password-stuffing, list-selling.

Our fix: `POST /auth/register` and `POST /auth/forgot-password` always return **202 Accepted** — whether the email exists or not. The attacker can't distinguish from the response. Small UX cost (no immediate "your account was created" confirmation), big security win.

## Rate limiting

We use Bucket4j on all `/api/auth/*` POSTs. Each IP has a bucket of 30 tokens that refills 30 tokens per 60 seconds. Each request costs 1 token. When the bucket is empty, server returns 429 Too Many Requests with a `Retry-After` header.

This kicks in **before** bcrypt is called, so brute-force attempts cost the server microseconds. A 10-million-attempt brute force becomes a 23-year campaign — not viable.

## Roles + @PreAuthorize

The JWT carries the user's role. The auth filter reads it and puts a `ROLE_OWNER` (or `ROLE_APPLICANT`, etc.) authority on Spring's `SecurityContext` for the request.

Controllers gate access with `@PreAuthorize("hasRole('OWNER')")`. If the context has the right role → method runs. If not → 403 Forbidden, method never executes.

Patterns:

- `hasRole('OWNER')` — must be an owner
- `hasAnyRole('OWNER', 'AGENT')` — either works
- `hasRole('ADMIN')` — admin-only
- `permitAll()` — public, no auth required

## Comparison to NestJS

For frontend engineers used to NestJS:

- Spring's `JwtAuthenticationFilter` ≈ NestJS's `JwtStrategy` + `JwtAuthGuard`
- `@PreAuthorize` ≈ NestJS's `@Roles` + `RolesGuard`
- `@AuthenticationPrincipal` ≈ NestJS's `@CurrentUser()`

One important difference: Spring runs the JWT filter on **every** request, populating the security context whether the endpoint is public or not. NestJS only runs guards on routes that opt in via `@UseGuards()`. The Spring approach lets public endpoints also know who's logged in (if there's an authenticated caller) and personalise responses — like marking which listings the current user has saved on a public browse endpoint.
