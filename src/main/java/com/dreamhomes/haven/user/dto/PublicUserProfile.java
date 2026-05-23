package com.dreamhomes.haven.user.dto;

import java.time.Instant;
import java.util.List;
import com.dreamhomes.haven.user.model.Role;

/**
 * Public projection of a user — what an unauthenticated visitor sees on
 * {@code GET /api/users/{id}/profile}. Deliberately minimal: no email, no phone, no
 * passwordHash, no tokenVersion. Carries the verified badges so the frontend can render
 * trust signals on agent / owner profile pages.
 *
 * <p>{@code agentCredentialVerifiedAt} is null unless {@link #role} is {@link Role#AGENT}.
 *
 * <p>The four agent-discovery fields ({@code serviceAreas}, {@code languages},
 * {@code specializationTags}, {@code feeSchedule}) come from {@code AgentProfile} per
 * PRD §4.2 ("fees, specializations, locations covered"). For non-agents (or agents who
 * haven't filled them in) the arrays are empty and {@code feeSchedule} is null — the
 * JSON shape stays stable across roles so the FE renderer doesn't have to branch.
 *
 * <p>{@code publicBio} is an optional public narrative (owners, agents, applicants);
 * null when unset.
 *
 * <p>{@code agentMarketingGallery} lists public marketing images for agents only; empty for
 * non-agents.
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
        long closedDealCount,
        Long medianResponseMinutes,
        Instant joinedAt,
        List<String> serviceAreas,
        List<String> languages,
        List<String> specializationTags,
        String feeSchedule,
        String publicBio,
        String profileImageUrl,
        List<PublicAgentMarketingItem> agentMarketingGallery
) {
}
