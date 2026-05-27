package com.dreamhomes.haven.dreamai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

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
        UserChoicePayload userChoice,
        @Schema(
                description = """
                        Item 23 — explicit ranking mode override. When omitted, the orchestrator
                        picks a sensible default (anonymous → FAST, authenticated → SMART).
                        Clients may force either mode regardless of auth state.
                        """,
                nullable = true)
        DreamAiRankMode rankMode,
        @Schema(
                description = """
                        Item 26 sub-task B — direct UI compare. When this list has 2–5 entries
                        the orchestrator routes straight to the compare path with those listing
                        ids, skipping URL extraction from the prompt. Lists of size <2 are
                        ignored (need at least 2 to compare); lists longer than 5 are capped.
                        Existing URL-paste compare still works for backward compatibility.
                        """,
                nullable = true)
        List<Long> compareListingIds
) {
        /** Back-compat factory for callers that don't supply the Item 23 / Item 26-B fields. */
        public DreamAiRunTurnRequest(String prompt, Long chatId, String clientMessageId, UserChoicePayload userChoice) {
                this(prompt, chatId, clientMessageId, userChoice, null, null);
        }
}
