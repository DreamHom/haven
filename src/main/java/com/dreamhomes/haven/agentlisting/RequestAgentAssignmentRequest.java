package com.dreamhomes.haven.agentlisting;

import jakarta.validation.constraints.NotNull;

public record RequestAgentAssignmentRequest(
        @NotNull Long agentId
) {
}
