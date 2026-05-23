package com.dreamhomes.haven.dreamai.chat.dto;

import com.dreamhomes.haven.dreamai.chat.model.DreamAiChatMessageRole;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "One persisted Dream AI message — USER text or typed ASSISTANT envelope.")
public record DreamAiChatMessageResponse(
        @Schema(description = "Stable message id.")
        Long id,
        DreamAiChatMessageRole role,
        @Schema(description = "Idempotency key echoed from the client (USER rows only).", nullable = true)
        String clientMessageId,
        @Schema(description = "Wire schema version of `content` in Postgres.", example = "1")
        int schemaVersion,
        @Schema(description = "USER plain text.", nullable = true)
        String userText,
        @Schema(description = "ASSISTANT / SYSTEM / TOOL structured turn.", nullable = true)
        AssistantTurnV1 assistantTurn,
        Instant createdAt
) {
}
