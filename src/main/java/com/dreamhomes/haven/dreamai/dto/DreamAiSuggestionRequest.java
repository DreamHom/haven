package com.dreamhomes.haven.dreamai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Natural-language description of the home the user is looking for.")
public record DreamAiSuggestionRequest(
        @NotBlank
        @Size(max = 500)
        String prompt,
        @Schema(
                description = "Existing thread id from a prior suggestion response; omit to start a new chat.",
                nullable = true)
        Long chatId
) {
    /** Back-compat for callers that only send `{ \"prompt\": \"...\" }`. */
    public DreamAiSuggestionRequest(String promptOnly) {
        this(promptOnly, null);
    }
}
