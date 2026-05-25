package com.dreamhomes.haven.verification.automation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Provider-agnostic shape returned by every {@link VerificationProvider} method.
 *
 * @param status            PASSED, FAILED, or NEEDS_HUMAN_REVIEW
 * @param score             confidence in [0,1]; null when the provider has no notion of one
 * @param extractedFields   OCR'd / parsed fields (NIN, name match score, etc.)
 * @param providerReference provider's own correlation id (useful in audit + support tickets)
 * @param rawResponse       provider's full JSON body — captured for forensics in JSONB
 * @param runAt             when the check ran (server time)
 */
public record AutomatedCheckResult(
        String status,
        BigDecimal score,
        Map<String, Object> extractedFields,
        String providerReference,
        String rawResponse,
        Instant runAt
) {
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NEEDS_HUMAN_REVIEW = "NEEDS_HUMAN_REVIEW";
}
