package com.dreamhomes.haven.verification.dto;

import java.time.Instant;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;

/**
 * Read-side projection of a {@link Verification} for API responses. {@code documentRefs}
 * is shipped as the raw JSON string — callers parse what they wrote in. We don't expose
 * {@code decisionReason} on submission responses; admin decision endpoints have their
 * own response shape (see admin package).
 */
public record VerificationResponse(
        Long id,
        VerificationType type,
        VerificationStatus status,
        Long submitterUserId,
        Long targetUserId,
        Long targetPropertyId,
        String documentRefs,
        Instant submittedAt,
        Instant decidedAt
) {
}
