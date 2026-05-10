package com.dreamhomes.haven.user.dto;

import java.time.Instant;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserAdminService;

/**
 * Admin-flavoured projection of a user. Carries the moderation-relevant fields
 * (suspendedAt) plus the verified-badge stamp; deliberately does not carry the
 * full PII surface (no phone, no fullName, no passwordHash).
 *
 * <p>Returned by {@link UserAdminService#suspend}, {@link UserAdminService#reactivate},
 * and {@link UserAdminService#findForAdmin}. The admin feature wraps this in its own
 * {@code AdminUserResponse} for the wire shape exposed to the dashboard.</p>
 */
public record UserAdminView(
        Long id,
        String email,
        String displayName,
        Role role,
        Instant suspendedAt,
        Instant identityVerifiedAt) {
}
