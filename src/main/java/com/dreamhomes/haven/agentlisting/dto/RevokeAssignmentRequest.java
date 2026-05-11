package com.dreamhomes.haven.agentlisting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeAssignmentRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
