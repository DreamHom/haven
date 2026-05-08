package com.dreamhomes.haven.user;

import java.time.Instant;

/**
 * Admin-flavoured projection of a user. Carries the moderation-relevant fields
 * (suspendedAt) plus the verified-badge stamp; deliberately does not carry the
 * full PII surface (no phone, no fullName, no passwordHash).
 *
 * <p>Returned by {@link UserAdminApi#suspend}, {@link UserAdminApi#reactivate},
 * and {@link UserAdminApi#findForAdmin}. The admin feature wraps this in its own
 * {@code AdminUserResponse} for the wire shape exposed to the dashboard.</p>
 */
public record UserAdminView(
        Long id,
        String email,
        Role role,
        Instant suspendedAt,
        Instant identityVerifiedAt) {
}
