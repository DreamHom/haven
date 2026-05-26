package com.dreamhomes.haven.admin.model;

/**
 * Identifies the kind of entity an {@link AdminAuditLog} row references. Stored as a
 * string in {@code admin_audit_log.target_type} so adding a new moderation surface is
 * code-only.
 */
public enum AuditTargetType {
    VERIFICATION,
    LISTING,
    USER,
    /** Phase 12: review takedowns. */
    REVIEW,
    PROMOTION
}
