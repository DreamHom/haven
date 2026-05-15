package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;

/**
 * Internal output of {@code AuthService.login}: the issued token plus the identity
 * facts the controller needs to build the public {@code LoginResponse}. Keeps the
 * controller free of any user-credential lookup.
 *
 * @param token              the JWT bearer string (no "Bearer " prefix)
 * @param userId             the user's id, mirrors what's in the JWT subject claim
 * @param role               the user's role at issue time
 * @param fullName           the user's display name — surfaced on the response so
 *                           the frontend can greet by name without a second call
 * @param expiresInSeconds   seconds until the JWT expires, derived from
 *                           {@code haven.jwt.expiration-ms}
 */
public record LoginResult(
        String token,
        Long userId,
        Role role,
        String fullName,
        long expiresInSeconds) {
}
