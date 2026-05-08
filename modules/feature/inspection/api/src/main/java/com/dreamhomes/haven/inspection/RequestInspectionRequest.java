package com.dreamhomes.haven.inspection;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestInspectionRequest(
        @NotNull Long slotId,
        @Size(max = 5000) String notes
) {
}
