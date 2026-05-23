package com.dreamhomes.haven.inspection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AgentRescheduleSlotRequest(
        @NotNull @Positive Long slotId
) {
}
