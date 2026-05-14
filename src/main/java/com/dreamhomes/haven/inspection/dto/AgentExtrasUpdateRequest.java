package com.dreamhomes.haven.inspection.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for {@code PATCH /api/inspections/{id}/agent/extras}. {@code null} clears the field;
 * blank trims to {@code null}.
 */
public record AgentExtrasUpdateRequest(
        @Size(max = 4000) String extras
) {
}
