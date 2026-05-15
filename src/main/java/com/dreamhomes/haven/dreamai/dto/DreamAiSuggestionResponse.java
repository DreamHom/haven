package com.dreamhomes.haven.dreamai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ordered LIVE listing ids: Claude-ranked when Anthropic is configured, else location-filtered browse.")
public record DreamAiSuggestionResponse(
        @Schema(description = "Up to 20 LIVE listing ids.")
        List<Long> listingIds,
        @Schema(
                description = "Persisted thread id when the suggestion was recorded for the caller; null for internal ranking-only calls.",
                nullable = true)
        Long chatId
) {
    public DreamAiSuggestionResponse(List<Long> listingIds) {
        this(listingIds, null);
    }
}
