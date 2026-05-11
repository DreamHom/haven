package com.dreamhomes.haven.verification.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import com.dreamhomes.haven.verification.model.VerificationType;

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
}
