package com.dreamhomes.haven.dreamai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Summary row for a Dream AI thread in the caller's history.")
public record DreamAiChatSummaryResponse(
        @Schema(description = "Thread id; send as `chatId` on the next suggestion request.")
        Long id,
        @Schema(description = "First user prompt, truncated for inbox previews.")
        String preview,
        @Schema(description = "When the thread was created.")
        Instant createdAt,
        @Schema(description = "Last activity on the thread (new suggestion turn).")
        Instant updatedAt
) {
}
