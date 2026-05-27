package com.dreamhomes.haven.user.service;

import com.dreamhomes.haven.agentmarketing.AgentMarketingMediaRepository;
import com.dreamhomes.haven.agentmarketing.model.AgentMarketingMedia;
import com.dreamhomes.haven.common.config.CacheConfig;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.review.dto.ReviewAggregate;
import com.dreamhomes.haven.review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.dreamhomes.haven.user.dto.PublicAgentMarketingItem;
import com.dreamhomes.haven.user.dto.PublicUserProfile;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
/**
 * Implementation of {@link UserProfileService}. Loads the {@code AgentProfile} only when the user's
 * role is {@link Role#AGENT} so the typical hit on an owner or applicant profile costs
 * one query, not two-or-three.
 *
 * <p>Pulls the review aggregate (average rating + count) through {@link ReviewService} so
 * trust signals render on profile cards without a follow-on GET. Cross-aggregate read
 * goes through the API only — never the review entity / repo.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final ReviewService reviewService;
    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;
    private final AgentMarketingMediaRepository agentMarketingMediaRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.USERS_PUBLIC_PROFILE, key = "#userId")
    public PublicUserProfile findPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getAccountDeletedAt() != null) {
            throw new UserNotFoundException(userId);
        }

        AgentProfile agentProfile = user.getRole() == Role.AGENT
                ? agentProfileRepository.findById(userId).orElse(null)
                : null;

        ReviewAggregate reviews = reviewService.aggregateForUser(userId);

        List<PublicAgentMarketingItem> gallery = user.getRole() == Role.AGENT
                ? agentMarketingMediaRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId).stream()
                        .map(UserProfileService::toPublicMarketingItem)
                        .toList()
                : Collections.emptyList();

        return toPublicProfile(user, agentProfile, reviews, gallery);
    }

    /**
     * Build the public-facing projection from a (user, optional-agent-profile, reviews) triple.
     * The agent-discovery fields ({@code serviceAreas}, {@code languages},
     * {@code specializationTags}, {@code feeSchedule}) come from {@code AgentProfile}; for
     * non-agents (and agents who haven't filled them in) the arrays default to empty and
     * the fee schedule is null so the JSON shape is identical across roles.
     */
    private PublicUserProfile toPublicProfile(User user, AgentProfile agentProfile, ReviewAggregate reviews,
            List<PublicAgentMarketingItem> agentMarketingGallery) {
        Instant credentialVerifiedAt = agentProfile == null ? null : agentProfile.getCredentialVerifiedAt();
        return new PublicUserProfile(
                user.getId(),
                user.getFullName(),
                user.getDisplayName(),
                user.getRole(),
                user.getIdentityVerifiedAt(),
                credentialVerifiedAt,
                user.getSuspendedAt() != null,
                reviews.averageRating(),
                reviews.count(),
                listingRepository.countByOwnerIdAndStatus(user.getId(), ListingStatus.CLOSED),
                roundedMedianMinutes(offerRepository.medianResponseMinutesForOwner(user.getId())),
                user.getCreatedAt(),
                safeList(agentProfile == null ? null : agentProfile.getServiceAreas()),
                safeList(agentProfile == null ? null : agentProfile.getLanguages()),
                safeList(agentProfile == null ? null : agentProfile.getSpecializationTags()),
                agentProfile == null ? null : agentProfile.getFeeSchedule(),
                user.getPublicBio(),
                user.getProfileImageUrl(),
                agentMarketingGallery);
    }

    private static PublicAgentMarketingItem toPublicMarketingItem(AgentMarketingMedia m) {
        return new PublicAgentMarketingItem(m.getId(), m.getUrl(), m.getCaption(), m.getDisplayOrder());
    }

    private static List<String> safeList(List<String> raw) {
        // Defensive: an AgentProfile row with a NULL array column (pre-V30 rows that somehow
        // weren't backfilled, or a future code path that nulls them out) still produces a
        // stable empty-array on the wire. The DB-level DEFAULT '{}' makes this near-unreachable
        // but the cost of the guard is one branch.
        return raw == null ? Collections.emptyList() : raw;
    }

    private static Long roundedMedianMinutes(Double minutes) {
        return minutes == null ? null : Math.round(minutes);
    }

    @Transactional(readOnly = true)
    public boolean exists(Long userId) {
        return userRepository.existsById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Role> roleOf(Long userId) {
        return userRepository.findById(userId).map(User::getRole);
    }

    /**
     * Public agent directory — non-suspended AGENT users. {@code q} is a case-insensitive
     * substring of name; {@code verified} requires identity-verified badge.
     * Persona audit (Biodun): "delegation-first product where the owner cannot find an
     * agent to delegate to is broken at the design level."
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PublicUserProfile> searchAgents(
            String q, boolean verified,
            org.springframework.data.domain.Pageable pageable) {
        return userRepository.searchAgents(q, verified, pageable)
                .map(user -> {
                    AgentProfile agentProfile = agentProfileRepository.findById(user.getId()).orElse(null);
                    ReviewAggregate reviews = reviewService.aggregateForUser(user.getId());
                    List<PublicAgentMarketingItem> gallery = user.getRole() == Role.AGENT
                            ? agentMarketingMediaRepository.findByUserIdOrderByDisplayOrderAscIdAsc(user.getId()).stream()
                                    .map(UserProfileService::toPublicMarketingItem)
                                    .toList()
                            : Collections.emptyList();
                    return toPublicProfile(user, agentProfile, reviews, gallery);
                });
    }
}
