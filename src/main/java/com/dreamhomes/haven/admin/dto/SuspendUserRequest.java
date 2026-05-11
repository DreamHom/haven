package com.dreamhomes.haven.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspendUserRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
