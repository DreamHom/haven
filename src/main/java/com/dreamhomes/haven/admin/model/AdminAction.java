package com.dreamhomes.haven.admin.model;

/**
 * The set of admin actions we audit. Stored as a string in {@code admin_audit_log.action}
 * so adding a new action is code-only — no migration required.
 */
public enum AdminAction {
    VERIFICATION_APPROVED,
    VERIFICATION_REJECTED,
    LISTING_APPROVED,
    LISTING_TAKEDOWN,
    USER_SUSPENDED,
    USER_REACTIVATED,
    /** Phase 12: admin took down a review for moderation. Author self-deletes don't write audit. */
    REVIEW_TAKEDOWN,
    PROMOTION_APPROVED,
    PROMOTION_REJECTED,
    PROMOTION_PAUSED,
    PROMOTION_RESUMED,
    PROMOTION_REVOKED
}
