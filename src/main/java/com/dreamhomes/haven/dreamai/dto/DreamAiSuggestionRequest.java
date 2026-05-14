package com.dreamhomes.haven.dreamai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Natural-language hint used to rank public listings (stub implementation).")
public record DreamAiSuggestionRequest(
        @NotBlank
        @Size(max = 500)
        String prompt
) {
}
