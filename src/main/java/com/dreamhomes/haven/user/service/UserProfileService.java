package com.dreamhomes.haven.user.service;

import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.review.dto.ReviewAggregate;
import com.dreamhomes.haven.review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
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

    @Transactional(readOnly = true)
    public PublicUserProfile findPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Instant credentialVerifiedAt = null;
        if (user.getRole() == Role.AGENT) {
            credentialVerifiedAt = agentProfileRepository.findById(userId)
                    .map(AgentProfile::getCredentialVerifiedAt)
                    .orElse(null);
        }

        ReviewAggregate reviews = reviewService.aggregateForUser(userId);

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
                listingRepository.countByOwnerIdAndStatus(userId, ListingStatus.CLOSED),
                roundedMedianMinutes(offerRepository.medianResponseMinutesForOwner(userId)),
                user.getCreatedAt());
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
     * substring of name; {@code verifiedOnly} requires identity-verified badge.
     * Persona audit (Biodun): "delegation-first product where the owner cannot find an
     * agent to delegate to is broken at the design level."
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PublicUserProfile> searchAgents(
            String q, boolean verifiedOnly,
            org.springframework.data.domain.Pageable pageable) {
        return userRepository.searchAgents(q, verifiedOnly, pageable)
                .map(user -> {
                    java.time.Instant credentialVerifiedAt = agentProfileRepository.findById(user.getId())
                            .map(com.dreamhomes.haven.user.model.AgentProfile::getCredentialVerifiedAt)
                            .orElse(null);
                    com.dreamhomes.haven.review.dto.ReviewAggregate reviews = reviewService.aggregateForUser(user.getId());
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
                            user.getCreatedAt());
                });
    }
}
