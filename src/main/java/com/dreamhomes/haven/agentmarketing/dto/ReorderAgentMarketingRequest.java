package com.dreamhomes.haven.agentmarketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "New display order: every gallery item id exactly once, first = lowest order.")
public record ReorderAgentMarketingRequest(
        @NotEmpty
        @Size(max = 64)
        List<@Positive Long> mediaIds
) {
}
