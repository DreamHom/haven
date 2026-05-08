package com.dreamhomes.haven.user;

import com.dreamhomes.haven.review.ReviewAggregate;
import com.dreamhomes.haven.review.ReviewApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of {@link UserApi}. Loads the {@code AgentProfile} only when the user's
 * role is {@link Role#AGENT} so the typical hit on an owner or applicant profile costs
 * one query, not two-or-three.
 *
 * <p>Pulls the review aggregate (average rating + count) through {@link ReviewApi} so
 * trust signals render on profile cards without a follow-on GET. Cross-aggregate read
 * goes through the API only — never the review entity / repo.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService implements UserApi {

    private final UserRepository userRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final ReviewApi reviewApi;

    @Override
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

        ReviewAggregate reviews = reviewApi.aggregateForUser(userId);

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

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long userId) {
        return userRepository.existsById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> roleOf(Long userId) {
        return userRepository.findById(userId).map(User::getRole);
    }
}
