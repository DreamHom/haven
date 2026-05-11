package com.dreamhomes.haven.user.dto;

import java.time.Instant;
import com.dreamhomes.haven.user.model.Role;

/**
 * Public projection of a user — what an unauthenticated visitor sees on
 * {@code GET /api/users/{id}/profile}. Deliberately minimal: no email, no phone, no
 * passwordHash, no tokenVersion. Carries the verified badges so the frontend can render
 * trust signals on agent / owner profile pages.
 *
 * <p>{@code agentCredentialVerifiedAt} is null unless {@link #role} is {@link Role#AGENT}.
 */
public record PublicUserProfile(
        Long id,
        String fullName,
        String displayName,
        Role role,
        Instant identityVerifiedAt,
        Instant agentCredentialVerifiedAt,
        boolean suspended,
        Double averageRating,
        Long reviewCount,
        Instant joinedAt
) {
}
