package com.dreamhomes.haven.review.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional reason for deletion. Authors deleting their own review can omit this;
 * admins are encouraged (not enforced) to supply one for the audit trail.
 *
 * <p>Was previously {@code @NotBlank} — the persona audit (Temi) flagged that
 * forcing a justification on self-delete is hostile to users.</p>
 */
public record DeleteReviewRequest(
        @Size(max = 1000) String reason
) {
}
