package com.dreamhomes.haven.inspection.model;

/**
 * Lifecycle of an inspection request.
 * <ul>
 *   <li>{@link #PENDING} — applicant submitted, owner hasn't acted yet. Locks the slot.</li>
 *   <li>{@link #APPROVED} — owner accepted. Still locks the slot.</li>
 *   <li>{@link #DECLINED} — owner rejected. Slot is freed for another request.</li>
 * </ul>
 *
 * <p>The partial unique index on {@code (slot_id) WHERE status IN ('PENDING','APPROVED')}
 * mirrors this rule at the database level.
 */
public enum InspectionRequestStatus {
    PENDING,
    APPROVED,
    DECLINED
}
