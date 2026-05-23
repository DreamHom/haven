package com.dreamhomes.haven.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public marketing gallery item on an agent profile")
public record PublicAgentMarketingItem(
        Long id,
        String url,
        String caption,
        int displayOrder
) {
}
