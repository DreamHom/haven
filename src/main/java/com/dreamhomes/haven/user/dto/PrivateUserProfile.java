package com.dreamhomes.haven.user.dto;

import com.dreamhomes.haven.user.model.Role;

import java.time.Instant;
import java.util.List;

/**
 * Private account-settings projection for the authenticated user. Sibling to
 * {@link PublicUserProfile} — that one is anonymous-visible and deliberately omits
 * email, phone, and license metadata; this one is gated behind a JWT subject match
 * and surfaces those fields so a settings page can preload its form inputs.
 *
 * <p>Carries {@code userId} (not {@code id}) to match {@code MeResponse} and the JWT
 * principal's vocabulary. The persona audit (Dayo) called out the mixed
 * {@code id}/{@code userId} field naming as a real frontend papercut — this DTO
 * stays on the {@code userId} side of the /me family.</p>
 *
 * <p>Agent-discovery fields ({@code serviceAreas}, {@code languages},
 * {@code specializationTags}, {@code feeSchedule}) shadow the columns on
 * {@code AgentProfile} so the settings form can preload them with one GET, then
 * round-trip the same shape on PATCH. For non-agents the arrays are empty and
 * {@code feeSchedule} is null — JSON shape is stable across roles.
 *
 * <p>{@code publicBio} mirrors {@link PublicUserProfile#publicBio()}; editable via
 * {@code PATCH /api/me}.
 */
public record PrivateUserProfile(
        Long userId,
        String email,
        String fullName,
        String displayName,
        String phone,
        Role role,
        Instant identityVerifiedAt,
        Instant agentCredentialVerifiedAt,
        String licenseNumber,
        String agency,
        boolean suspended,
        Instant joinedAt,
        List<String> serviceAreas,
        List<String> languages,
        List<String> specializationTags,
        String feeSchedule,
        String publicBio,
        String profileImageUrl,
        String notificationPreferences
) {
}
