package com.dreamhomes.haven.verification.automation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wire shape of one automated verification check result. Embedded as a list on the
 * verification response so callers (and admins) can see what the provider extracted.
 *
 * <p>**In v1 every row carries {@code providerName = "MOCK"}** — see Item 20 in
 * {@code docs/demo-prep/post-session-tasks.md}. Frontend integrators should not assume
 * the extracted fields are real until v2 swaps to a production provider.
 */
@Schema(description = """
        Result of one automated verification check. In v1 the provider is MOCKED \
        (status=PASSED, score=0.95). v2 swaps the provider via the \
        {@code HAVEN_VERIFICATION_PROVIDER} env var.""")
public record AutomatedCheckResultResponse(
        @Schema(example = "OWNER_IDENTITY") String checkType,
        @Schema(example = "MOCK") String providerName,
        @Schema(example = "PASSED") String status,
        @Schema(example = "0.95") BigDecimal score,
        @Schema(description = "OCR'd / parsed fields. JSON object as string.",
                example = "{\"nin\":\"12345678901\",\"nameMatch\":0.98}")
        String extractedFields,
        @Schema(example = "mock-owner-99") String providerReference,
        @Schema(example = "2026-05-24T08:30:00Z") Instant runAt
) {
    public static AutomatedCheckResultResponse from(VerificationAutomationResult row) {
        return new AutomatedCheckResultResponse(
                row.getCheckType(),
                row.getProviderName(),
                row.getStatus(),
                row.getScore(),
                row.getExtractedFields(),
                row.getProviderReference(),
                row.getRunAt());
    }
}
