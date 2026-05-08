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
    public static AgentListingResponse from(AgentListing al) {
        return new AgentListingResponse(
                al.getId(), al.getListingId(), al.getAgentUserId(),
                al.getRequestedByOwnerId(), al.getStatus(), al.getDecisionReason(),
                al.getRequestedAt(), al.getDecidedAt());
    }
}
