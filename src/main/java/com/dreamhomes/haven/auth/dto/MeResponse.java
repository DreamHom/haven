package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;

/**
 * Public {@code GET /api/me} response. Identity-only — does not surface
 * {@code tokenVersion} (which is an internal revocation primitive end users
 * shouldn't see) and pulls {@code fullName} so the frontend can greet by
 * name on app boot without a second call to {@code /users/{me}/profile}.
 */
public record MeResponse(Long userId, String email, String fullName, Role role) {
}
