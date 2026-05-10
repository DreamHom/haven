package com.dreamhomes.haven.agentlisting.dto;

import jakarta.validation.constraints.NotNull;

public record RequestAgentAssignmentRequest(
        @NotNull Long agentId
) {
}
