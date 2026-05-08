package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.verification.VerificationStatus;
import com.dreamhomes.haven.verification.VerificationType;

import java.time.Instant;

/**
 * Admin-side projection of a verification. Includes the decision metadata
 * (decidedAt / decidedByAdminId / decisionReason) that the submitter-side response
 * deliberately omits — admins need full forensic context.
 */
public record AdminVerificationResponse(
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
        String decisionReason
) {
}
