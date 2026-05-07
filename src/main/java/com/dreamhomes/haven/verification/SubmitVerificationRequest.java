package com.dreamhomes.haven.verification;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Wire-format request body for {@code POST /api/verifications}. {@code propertyId} must
 * be supplied for {@link VerificationType#PROPERTY_DOCUMENTS} and ignored otherwise; the
 * service is the source of truth for that rule.
 */
public record SubmitVerificationRequest(
        @NotNull VerificationType type,
        Long propertyId,
        @NotEmpty Map<String, Object> documentRefs
) {
    public SubmitVerificationCommand toCommand() {
        return new SubmitVerificationCommand(type, propertyId, documentRefs);
    }
}
