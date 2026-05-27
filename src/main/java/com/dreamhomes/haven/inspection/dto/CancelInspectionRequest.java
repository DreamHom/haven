package com.dreamhomes.haven.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/inspections/{id}/cancel} (Gap C of post-session-tasks Item
 * 7). The reason is REQUIRED — the receiving party will see it on their in-tray
 * notification so they understand what happened.
 */
public record CancelInspectionRequest(
        @NotBlank
        @Size(max = 200)
        String reason
) {
}
