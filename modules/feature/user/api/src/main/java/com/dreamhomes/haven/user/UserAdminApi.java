package com.dreamhomes.haven.user;

import java.time.Instant;

/**
 * Admin-facing slice of the user feature. Replaces direct {@code UserRepository} +
 * {@code AgentProfileRepository} writes from {@code feature/admin/impl} — that direct
 * impl-impl edge was the original "admin reaches into user" exception in
 * {@code TRADEOFFS.md}. After this api, admin-impl compiles against
 * {@code feature/user/api} only.
 *
 * <p>The api is intentionally narrow: only the moderation actions and badge stamps
 * admin actually performs. Audit-log writes and notifications stay on the admin
 * side because they are admin's cross-cutting concerns; the user-impl side just
 * mutates user state and reports back what changed.</p>
 */
public interface UserAdminApi {

    /**
     * Suspend a user. Sets {@code suspendedAt = now} and bumps {@code tokenVersion}
     * so every outstanding JWT for the user is rejected on the next request.
     *
     * @throws UserNotFoundException        if the user doesn't exist
     * @throws UserAlreadySuspendedException if the user is already suspended
     */
    UserAdminView suspend(Long userId);

    /**
     * Reactivate a suspended user. Clears {@code suspendedAt}; does NOT bump
     * {@code tokenVersion} (the suspend bump already invalidated outstanding JWTs;
     * a re-bump would be wasted churn).
     *
     * @throws UserNotFoundException     if the user doesn't exist
     * @throws UserNotSuspendedException if the user isn't suspended
     */
    UserAdminView reactivate(Long userId);

    /**
     * Stamp the {@code identity_verified_at} badge on the user. Called by
     * {@code VerificationAdminApi} when an OWNER_IDENTITY or APPLICANT_IDENTITY
     * verification is approved. Idempotent: re-calling overwrites with the new
     * timestamp.
     *
     * @throws UserNotFoundException if the target user is gone (shouldn't happen —
     *         the verification row guarantees the user existed at submission time)
     */
    void markIdentityVerified(Long userId, Instant when);

    /**
     * Stamp the {@code credential_verified_at} badge on the agent profile. Called by
     * {@code VerificationAdminApi} when an AGENT_CREDENTIALS verification is approved.
     *
     * @throws AgentProfileNotFoundException if the user has role=AGENT but no
     *         {@code AgentProfile} row (shouldn't happen — registration creates both
     *         atomically)
     */
    void markAgentCredentialVerified(Long userId, Instant when);

    /**
     * Read-side projection used by admin controllers when they need to surface user
     * data after a moderation action. Returns the same shape as {@link #suspend} /
     * {@link #reactivate}.
     */
    UserAdminView findForAdmin(Long userId);
}
