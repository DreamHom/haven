package com.dreamhomes.haven.admin;

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
    USER_REACTIVATED
}
