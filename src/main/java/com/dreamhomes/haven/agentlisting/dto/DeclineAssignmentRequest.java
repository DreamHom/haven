package com.dreamhomes.haven.agentlisting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeclineAssignmentRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
