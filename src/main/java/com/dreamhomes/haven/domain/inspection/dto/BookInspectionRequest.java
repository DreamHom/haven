package com.dreamhomes.haven.domain.inspection.dto;

import jakarta.validation.constraints.NotNull;

public record BookInspectionRequest(
        @NotNull Long slotId,
        @NotNull Long applicantId
) {}

