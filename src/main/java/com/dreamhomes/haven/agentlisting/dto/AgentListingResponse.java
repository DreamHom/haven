package com.dreamhomes.haven.agentlisting.dto;

import java.time.Instant;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
public record AgentListingResponse(
        Long id,
        Long listingId,
        Long agentUserId,
        Long requestedByOwnerId,
        AgentListingStatus status,
        String decisionReason,
        Instant requestedAt,
        Instant decidedAt
) {
}
