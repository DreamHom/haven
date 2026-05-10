package com.dreamhomes.haven.verification.model;

/**
 * Lifecycle of a {@link Verification} row. PENDING is the only state from which
 * a row can transition; APPROVED and REJECTED are terminal. Re-submitting after
 * a rejection creates a new row (with the old one preserved as audit history).
 */
public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
