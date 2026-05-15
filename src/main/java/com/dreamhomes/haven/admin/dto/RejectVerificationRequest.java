package com.dreamhomes.haven.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/admin/verifications/{id}/reject}. The reason is required
 * and surfaced to the submitter in the rejection notification — keep it actionable.
 */
public record RejectVerificationRequest(
        // @NotBlank rejects empty / whitespace-only at the validator. @Size(min = 1)
        // is added so OpenAPI generates `minLength: 1` on the schema — without it
        // the spec advertised `minLength: 0` and contradicted the persona-doc
        // contract that empty reasons return 400.
        @NotBlank @Size(min = 1, max = 1000) String reason
) {
}
