package com.dreamhomes.haven.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import com.dreamhomes.haven.verification.model.VerificationType;

/**
 * Wire-format request body for {@code POST /api/verifications}. {@code propertyId} must
 * be supplied for {@link VerificationType#PROPERTY_DOCUMENTS} and ignored otherwise; the
 * service is the source of truth for that rule.
 *
 * <p>{@code livenessCheckId} is optional and links to a row from
 * {@code POST /api/verifications/liveness-check} — see Item 19 in
 * {@code docs/demo-prep/post-session-tasks.md} for the MOCKED-v1 framing.
 */
public record SubmitVerificationRequest(
        @NotNull VerificationType type,
        Long propertyId,
        @NotEmpty Map<String, Object> documentRefs,
        @Schema(
                description = """
                        (MOCKED v1) Reference to a passed liveness check from \
                        {@code POST /api/verifications/liveness-check}. v2 will require \
                        this to come from a real provider; v1 accepts any caller's own \
                        mocked PASSED row. Validates ownership + unconsumed-ness; the same \
                        liveness id cannot be used twice across submissions.""",
                example = "42",
                nullable = true)
        Long livenessCheckId
) {
}
