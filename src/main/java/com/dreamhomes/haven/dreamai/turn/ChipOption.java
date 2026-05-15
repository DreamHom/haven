package com.dreamhomes.haven.dreamai.turn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quick-reply chip for clarify flows — client sends `sendText` as the next user message (v1).")
public record ChipOption(
        @Schema(example = "budget-under-5m") String id,
        @Schema(example = "Under ₦5M") String label,
        @Schema(description = "Plain text posted as the next user turn when the chip is tapped.", example = "My budget is under 5 million naira")
        String sendText
) {
    @JsonCreator
    public ChipOption(
            @JsonProperty("id") String id,
            @JsonProperty("label") String label,
            @JsonProperty("sendText") String sendText) {
        this.id = id;
        this.label = label;
        this.sendText = sendText;
    }
}
