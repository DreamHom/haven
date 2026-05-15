package com.dreamhomes.haven.auth.refresh;

import java.time.Instant;

/**
 * Internal carrier for a freshly minted refresh token. The raw {@code token}
 * string is the value the client gets back exactly once; the persisted row
 * stores only the SHA-256 hash. {@code expiresAt} mirrors the row's column so
 * the controller can surface a {@code refreshExpiresInSeconds} on the response
 * without re-loading anything.
 */
public record IssuedRefreshToken(String token, Instant expiresAt) {
}
