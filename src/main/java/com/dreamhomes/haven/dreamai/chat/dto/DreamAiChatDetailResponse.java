package com.dreamhomes.haven.dreamai.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Full thread with messages oldest-first.")
public record DreamAiChatDetailResponse(
        DreamAiChatSummaryResponse chat,
        List<DreamAiChatMessageResponse> messages
) {
}
