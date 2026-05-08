package com.dreamhomes.haven.admin;

import java.util.Map;

/**
 * Public contract for writing rows to the admin audit log. Used by features that perform
 * admin-driven mutations from outside the admin module — e.g. {@code feature-review-impl}
 * when an admin takes down a review. The full admin moderation surface stays inside
 * {@code feature-admin-impl}; this is the narrow slice that crosses module boundaries.
 */
public interface AdminAuditApi {

    /**
     * Append an audit row.
     *
     * @param adminId  the user id of the admin performing the action
     * @param action   classification (e.g. REVIEW_TAKEDOWN, USER_SUSPENDED)
     * @param targetType which aggregate the action targets
     * @param targetId   id within that aggregate
     * @param metadata   arbitrary context — the impl serialises to JSON
     */
    void record(Long adminId, AdminAction action, AuditTargetType targetType,
                Long targetId, Map<String, Object> metadata);
}
