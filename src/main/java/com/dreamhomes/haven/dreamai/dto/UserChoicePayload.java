package com.dreamhomes.haven.dreamai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Client-issued choice from a clarify chip — prefer `sendText` as the next effective prompt.")
public record UserChoicePayload(
        @Schema(description = "Stable chip id echoed from the server.", nullable = true)
        String chipId,
        @Schema(description = "Text used as the next user prompt when non-blank.", nullable = true)
        String sendText
) {
    @JsonCreator
    public UserChoicePayload(
            @JsonProperty("chipId") String chipId,
            @JsonProperty("sendText") String sendText) {
        this.chipId = chipId;
        this.sendText = sendText;
    }
}
