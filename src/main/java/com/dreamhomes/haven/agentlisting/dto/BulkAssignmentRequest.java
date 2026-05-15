package com.dreamhomes.haven.agentlisting.dto;

import jakarta.validation.constraints.NotNull;

/**
 * One row inside {@code POST /api/agent-listings/bulk}. Pairs a listing with the agent
 * the owner wants to invite. The owner of every listed listing must match the caller —
 * the service enforces it the same way the single-item endpoint does.
 */
public record BulkAssignmentRequest(
        @NotNull Long listingId,
        @NotNull Long agentId
) {
}
