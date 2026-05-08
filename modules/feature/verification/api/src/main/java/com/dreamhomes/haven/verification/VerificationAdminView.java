package com.dreamhomes.haven.verification;

import java.time.Instant;

/**
 * Admin-side projection of a verification. Includes the decision metadata
 * (decidedAt / decidedByAdminId / decisionReason) that the submitter-side
 * {@code VerificationResponse} deliberately omits — admins need full forensic
 * context.
 *
 * <p>Returned by {@link VerificationAdminApi#listPending},
 * {@link VerificationAdminApi#approve}, and {@link VerificationAdminApi#reject}.
 * The admin feature wraps this in its own {@code AdminVerificationResponse} for
 * the dashboard wire shape.</p>
 */
public record VerificationAdminView(
        Long id,
        VerificationType type,
        VerificationStatus status,
        Long submitterUserId,
        Long targetUserId,
        Long targetPropertyId,
        String documentRefs,
        Instant submittedAt,
        Instant decidedAt,
        Long decidedByAdminId,
        String decisionReason) {
}
