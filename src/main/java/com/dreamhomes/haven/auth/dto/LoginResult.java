package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;

/**
 * Internal output of {@code AuthService.login} (and {@code AuthService.refresh}):
 * the issued access token plus the identity facts the controller needs to build
 * the public {@code LoginResponse}, plus the freshly minted refresh token + its
 * expiry. Keeps the controller free of any user-credential lookup.
 *
 * @param token                     the JWT bearer string (no "Bearer " prefix)
 * @param userId                    the user's id, mirrors what's in the JWT subject claim
 * @param role                      the user's role at issue time
 * @param fullName                  the user's display name — surfaced on the response so
 *                                  the frontend can greet by name without a second call
 * @param expiresInSeconds          seconds until the access JWT expires, derived from
 *                                  {@code haven.jwt.expiration-ms}
 * @param refreshToken              the long-lived refresh token (raw, opaque, shown
 *                                  exactly once — only the SHA-256 hash is persisted)
 * @param refreshExpiresInSeconds   seconds until the refresh token expires, derived from
 *                                  {@code haven.jwt.refresh-expiration-ms}
 */
public record LoginResult(
        String token,
        Long userId,
        Role role,
        String fullName,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds) {
}
