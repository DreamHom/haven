package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;

/**
 * Public response from {@code POST /api/auth/login}. Enriched with identity facts so
 * the client doesn't need a follow-up {@code GET /me} call to display the user's
 * name + role + how long the session is good for.
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        Long userId,
        Role role,
        String fullName) {
}
