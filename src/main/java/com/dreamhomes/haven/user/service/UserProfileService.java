package com.dreamhomes.haven.user.service;

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
                user.getRole(),
                user.getIdentityVerifiedAt(),
                credentialVerifiedAt,
                user.getSuspendedAt() != null,
                reviews.averageRating(),
                reviews.count(),
                user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public boolean exists(Long userId) {
        return userRepository.existsById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Role> roleOf(Long userId) {
        return userRepository.findById(userId).map(User::getRole);
    }
}
