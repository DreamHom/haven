package com.dreamhomes.haven.dreamai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ordered listing ids suggested for the prompt (stub).")
public record DreamAiSuggestionResponse(
        @Schema(description = "Up to 20 LIVE listing ids.")
        List<Long> listingIds
) {
}
