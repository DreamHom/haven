package com.dreamhomes.haven.user;

import com.dreamhomes.haven.admin.UserNotFoundException;
import com.dreamhomes.haven.review.ReviewAggregate;
import com.dreamhomes.haven.review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service for public profile pages. Loads the {@code AgentProfile} only when
 * the user's role is {@link Role#AGENT} so the typical hit on an owner or applicant
 * profile costs one query, not two-or-three.
 *
 * <p>Also pulls the review aggregate (average rating + count) so trust signals render
 * on profile cards without a follow-on GET.
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

        java.time.Instant credentialVerifiedAt = null;
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
}
