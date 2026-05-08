package com.dreamhomes.haven.user;

import java.util.Optional;

/**
 * Public contract for the user feature. Cross-feature consumers wire this interface —
 * they never see the {@code User} entity, the repository, or any other implementation
 * detail. The implementation is {@code com.dreamhomes.haven.user.UserProfileService} in
 * {@code feature/user/impl}.
 *
 * <p>Note: {@link Role} stays in {@code core} (split-package convention) because it's a
 * security primitive used by JWT, {@code @PreAuthorize}, and the {@code JwtPrincipal}
 * across every feature — not a user-feature-private type.
 */
public interface UserApi {

    /**
     * Read public profile for cross-feature display (browse cards, agent picker, review
     * lists). Bundles the verified-badge timestamps and review aggregate so the consumer
     * can render trust signals without a follow-on call.
     *
     * @throws UserNotFoundException if no user exists with this id
     */
    PublicUserProfile findPublicProfile(Long userId);

    /** Cheap existence check — used in admin/agent paths that just need a yes/no. */
    boolean exists(Long userId);

    /** Role lookup — used by admin verification badge flips and agent-role gating. */
    Optional<Role> roleOf(Long userId);
}
