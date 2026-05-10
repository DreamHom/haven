package com.dreamhomes.haven.agentlisting;

import java.time.Instant;

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
