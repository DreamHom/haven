package com.dreamhomes.haven.dreamai.turn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Diagnostics for a Dream AI assistant turn — empty inventory vs strict query, degraded paths.")
public record TurnMeta(
        @Schema(description = "True when the LIVE catalogue had zero candidates before ranking.", nullable = true)
        Boolean inventoryEmpty,
        @Schema(description = "True when candidates existed but the ranker returned no ids (prompt too strict / model mismatch).", nullable = true)
        Boolean queryTooStrict,
        @Schema(description = "Anthropic ranking unavailable — location stub or embeddings-only path.", nullable = true)
        Boolean degraded,
        @Schema(description = "Upstream provider id when applicable: anthropic | stub", nullable = true)
        String provider,
        @Schema(description = "Request correlation id — logs and SSE `trace` event use the same value.", nullable = true)
        String traceId,
        @Schema(description = "True when user message was blocked by Haven moderation (non-retryable).", nullable = true)
        Boolean moderationBlocked,
        @Schema(description = "Client may retry once when true (timeouts / 5xx classification).", nullable = true)
        Boolean retryable,
        @Schema(description = "True when stale listing ids were stripped on thread read.", nullable = true)
        Boolean staleIdsFiltered
) {
    public static TurnMeta empty() {
        return new TurnMeta(null, null, null, null, null, null, null, null);
    }

    @JsonCreator
    public TurnMeta(
            @JsonProperty("inventoryEmpty") Boolean inventoryEmpty,
            @JsonProperty("queryTooStrict") Boolean queryTooStrict,
            @JsonProperty("degraded") Boolean degraded,
            @JsonProperty("provider") String provider,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("moderationBlocked") Boolean moderationBlocked,
            @JsonProperty("retryable") Boolean retryable,
            @JsonProperty("staleIdsFiltered") Boolean staleIdsFiltered) {
        this.inventoryEmpty = inventoryEmpty;
        this.queryTooStrict = queryTooStrict;
        this.degraded = degraded;
        this.provider = provider;
        this.traceId = traceId;
        this.moderationBlocked = moderationBlocked;
        this.retryable = retryable;
        this.staleIdsFiltered = staleIdsFiltered;
    }
}
