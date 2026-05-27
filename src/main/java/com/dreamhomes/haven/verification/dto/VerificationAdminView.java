package com.dreamhomes.haven.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import com.dreamhomes.haven.verification.automation.AutomatedCheckResultResponse;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;
import com.dreamhomes.haven.verification.service.VerificationAdminService;
/**
 * Admin-side projection of a verification. Includes the decision metadata
 * (decidedAt / decidedByAdminId / decisionReason) that the submitter-side
 * {@code VerificationResponse} deliberately omits — admins need full forensic
 * context.
 *
 * <p>Returned by {@link VerificationAdminService#listPending},
 * {@link VerificationAdminService#approve}, and {@link VerificationAdminService#reject}.
 * The admin feature wraps this in its own {@code AdminVerificationResponse} for
 * the dashboard wire shape.</p>
 *
 * <p>{@code automatedChecks} surfaces the per-submission {@link AutomatedCheckResultResponse}
 * rows so admins see what the provider extracted before they decide. **v1 is MOCKED**
 * (provider = "MOCK", all PASS) — admins are still the source of truth. Item 20 in
 * {@code docs/demo-prep/post-session-tasks.md}.</p>
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
        String decisionReason,
        @Schema(
                description = """
                        Automated checks that ran when this verification was submitted. \
                        **v1 is MOCKED** — provider = "MOCK", status = PASSED, score = 0.95 \
                        with plausible mock-extracted fields. Admin is still the source of \
                        truth; this exists so the queue UI can show "Mock provider says \
                        PASSED — does the extracted NIN match the document?".""",
                nullable = true)
        List<AutomatedCheckResultResponse> automatedChecks) {
}
