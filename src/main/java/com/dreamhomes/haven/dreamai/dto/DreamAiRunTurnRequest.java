package com.dreamhomes.haven.dreamai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@Schema(description = "Authenticated Dream AI turn — plain prompt and/or structured chip reply (v1).")
public record DreamAiRunTurnRequest(
        @Schema(description = "User text — may be omitted when `userChoice.sendText` is set.", nullable = true)
        @Size(max = 500)
        String prompt,
        @Schema(description = "Continue an existing thread.", nullable = true)
        Long chatId,
        @Schema(description = "Idempotency key — replays return the same assistant envelope.", nullable = true)
        @Size(max = 64)
        String clientMessageId,
        @Valid
        @Schema(description = "Structured follow-up from a clarify chip (v1).", nullable = true)
        UserChoicePayload userChoice
) {
}
