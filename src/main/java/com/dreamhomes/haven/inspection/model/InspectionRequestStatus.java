package com.dreamhomes.haven.inspection.model;

/**
 * Lifecycle of an inspection request.
 * <ul>
 *   <li>{@link #PENDING} — applicant submitted, owner hasn't acted yet. Locks the slot.</li>
 *   <li>{@link #APPROVED} — owner accepted. Still locks the slot.</li>
 *   <li>{@link #DECLINED} — owner rejected. Slot is freed for another request.</li>
 *   <li>{@link #CANCELLED} — applicant withdrew before the owner responded. Slot is freed.
 *       Persona audit (Temi): "if something comes up at work I genuinely cannot cancel".</li>
 * </ul>
 *
 * <p>The partial unique index on {@code (slot_id) WHERE status IN ('PENDING','APPROVED')}
 * mirrors this rule at the database level.
 */
public enum InspectionRequestStatus {
    PENDING,
    APPROVED,
    DECLINED,
    CANCELLED
}
