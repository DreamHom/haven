package com.dreamhomes.haven.dreamai.chat.payload;

import com.dreamhomes.haven.dreamai.chat.model.DreamAiChatMessageRole;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wire-stable JSON stored in {@code dream_ai_chat_messages.content} (JSONB).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Persisted Dream AI message document — versioned for forward-compatible reads.")
public record DreamAiMessageDocumentV1(
        @Schema(description = "Schema version — bump when breaking stored shape.", example = "1")
        int schemaVersion,
        DreamAiChatMessageRole role,
        @Schema(description = "Client-supplied idempotency key on USER rows.", nullable = true)
        String clientMessageId,
        @Schema(description = "USER plain text (v1).", nullable = true)
        String userText,
        @Schema(description = "ASSISTANT / SYSTEM / TOOL structured turn.", nullable = true)
        AssistantTurnV1 turn
) {
    public static final int CURRENT = 1;

    @JsonCreator
    public DreamAiMessageDocumentV1(
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("role") DreamAiChatMessageRole role,
            @JsonProperty("clientMessageId") String clientMessageId,
            @JsonProperty("userText") String userText,
            @JsonProperty("turn") AssistantTurnV1 turn) {
        this.schemaVersion = schemaVersion;
        this.role = role;
        this.clientMessageId = clientMessageId;
        this.userText = userText;
        this.turn = turn;
    }

    public static DreamAiMessageDocumentV1 user(String text, String clientMessageId) {
        return new DreamAiMessageDocumentV1(CURRENT, DreamAiChatMessageRole.USER, clientMessageId, text, null);
    }

    public static DreamAiMessageDocumentV1 assistant(AssistantTurnV1 turn) {
        return new DreamAiMessageDocumentV1(CURRENT, DreamAiChatMessageRole.ASSISTANT, null, null, turn);
    }
}
