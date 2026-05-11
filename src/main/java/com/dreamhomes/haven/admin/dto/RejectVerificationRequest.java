package com.dreamhomes.haven.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/admin/verifications/{id}/reject}. The reason is required
 * and surfaced to the submitter in the rejection notification — keep it actionable.
 */
public record RejectVerificationRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
