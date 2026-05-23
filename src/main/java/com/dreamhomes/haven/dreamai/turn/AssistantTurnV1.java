package com.dreamhomes.haven.dreamai.turn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * MVP assistant turn envelope — clients branch on {@link #kind()} and optional {@link #blocks()}.
 * <p><b>Phase 2</b> (not wire-stable yet): streaming markdown deltas over SSE; tool-role rows;
 * richer {@link TurnMeta} for moderation and multi-step tools.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = """
        Version-1 assistant payload returned from Dream AI turn endpoints and echoed in thread history.
        Use `kind` to pick UI layout; `blocks` carry structured listing/compare/chip data; `markdown` is optional narrative.
        """)
public record AssistantTurnV1(
        @Schema(description = "MVP kinds: reply | clarify | no_results | compare | error", example = "reply")
        DreamAiTurnKind kind,
        @Schema(description = "Optional prose (streamed over SSE in phase 2; single chunk in MVP).", nullable = true)
        String markdown,
        @Schema(description = "Structured blocks — listings rail, compare table, or clarification chips.")
        List<TurnBlock> blocks,
        @Schema(description = "Diagnostics: empty inventory vs strict query, degraded ranking, trace id.")
        TurnMeta meta
) {
    public AssistantTurnV1 {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        meta = meta == null ? TurnMeta.empty() : meta;
    }
}
