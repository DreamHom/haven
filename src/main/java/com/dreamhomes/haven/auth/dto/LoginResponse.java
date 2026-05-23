package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;

/**
 * Public response from {@code POST /api/auth/login} and {@code POST /api/auth/refresh}.
 * Enriched with identity facts so the client doesn't need a follow-up {@code GET /me}
 * call to display the user's name + role + how long the session is good for.
 *
 * <p>{@code refreshToken} is the long-lived opaque credential the client stores
 * (httpOnly cookie or secure storage) and exchanges for a new access token at
 * {@code POST /api/auth/refresh}. Shown exactly once — the server only keeps a
 * SHA-256 hash, so it cannot be re-issued or recovered.</p>
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds,
        Long userId,
        Role role,
        String fullName) {
}
