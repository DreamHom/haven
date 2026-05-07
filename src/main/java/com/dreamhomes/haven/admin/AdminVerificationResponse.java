package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.verification.Verification;
import com.dreamhomes.haven.verification.VerificationStatus;
import com.dreamhomes.haven.verification.VerificationType;

import java.time.Instant;

/**
 * Admin-side projection of a {@link Verification}. Includes the decision metadata
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
    public static AdminVerificationResponse from(Verification v) {
        return new AdminVerificationResponse(
                v.getId(), v.getType(), v.getStatus(),
                v.getSubmitterUserId(), v.getTargetUserId(), v.getTargetPropertyId(),
                v.getDocumentRefs(), v.getSubmittedAt(),
                v.getDecidedAt(), v.getDecidedByAdminId(), v.getDecisionReason());
    }
}
