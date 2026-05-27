package com.dreamhomes.haven.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import com.dreamhomes.haven.verification.automation.AutomatedCheckResultResponse;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;

/**
 * Read-side projection of a {@link Verification} for API responses. {@code documentRefs}
 * is shipped as the raw JSON string — callers parse what they wrote in.
 *
 * <p>{@code decisionReason} is populated only when {@code status == REJECTED} so the
 * submitter knows what to fix on resubmit (Item 21, {@code docs/demo-prep/post-session-tasks.md}).
 * It is intentionally null on PENDING / APPROVED — there is no useful reason to expose
 * on those states, and not leaking a stale value from a prior decision cycle keeps the
 * field's meaning unambiguous on every status.
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
        Instant decidedAt,
        @Schema(
                description = """
                        Only populated when {@code status == REJECTED}. The reason supplied \
                        by the admin who rejected this verification — show prominently to the \
                        user so they know what to fix on resubmit. Null on PENDING / APPROVED \
                        rows (never leaks stale values from a prior decision cycle).
                        """,
                example = "Photo too blurry, retake in better light.",
                nullable = true)
        String decisionReason,
        @Schema(
                description = """
                        Results of the automated verification checks run when this verification \
                        was submitted. **In v1 these are MOCKED** (provider = "MOCK", all PASS). \
                        In v2, replace the provider via {@code HAVEN_VERIFICATION_PROVIDER} \
                        env var — supported: smile-id, dojah. Each entry's {@code providerName} \
                        field tells the caller which provider produced it. Null when no checks \
                        ran (e.g. legacy rows from before Item 20 shipped).""",
                nullable = true)
        List<AutomatedCheckResultResponse> automatedChecks
) {
}
