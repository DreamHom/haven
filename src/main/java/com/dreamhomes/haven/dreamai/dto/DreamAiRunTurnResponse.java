package com.dreamhomes.haven.dreamai.dto;

import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Dream AI turn result — structured assistant envelope plus legacy listing id list.")
public record DreamAiRunTurnResponse(
        @Schema(description = "Thread id for follow-up turns.")
        Long chatId,
        @Schema(description = "Correlation id — logs, SSE `trace` event, and `turn.meta.traceId` align.")
        String traceId,
        @Schema(description = "Typed assistant payload — UI branches on `turn.kind` and `turn.blocks`.")
        AssistantTurnV1 turn,
        @Schema(description = "Duplicate of listing ids from the listings block when `kind=reply` (backward compat).")
        List<Long> listingIds
) {
}
