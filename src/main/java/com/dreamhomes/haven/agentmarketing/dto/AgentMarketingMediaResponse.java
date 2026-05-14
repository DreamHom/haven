package com.dreamhomes.haven.agentmarketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Agent-owned marketing gallery item")
public record AgentMarketingMediaResponse(
        Long id,
        String url,
        String caption,
        int displayOrder,
        Instant uploadedAt
) {
}
