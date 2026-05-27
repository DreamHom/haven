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
        @Schema(description = "High-level provider tag: anthropic | stub | embeddings-only | compare | orchestrator | none. Preserved for backwards-compat — see `llmProvider` / `embeddingProvider` for the precise vendor that ran.", nullable = true)
        String provider,
        @Schema(description = "Request correlation id — logs and SSE `trace` event use the same value.", nullable = true)
        String traceId,
        @Schema(description = "True when user message was blocked by Haven moderation (non-retryable).", nullable = true)
        Boolean moderationBlocked,
        @Schema(description = "Client may retry once when true (timeouts / 5xx classification).", nullable = true)
        Boolean retryable,
        @Schema(description = "True when stale listing ids were stripped on thread read.", nullable = true)
        Boolean staleIdsFiltered,
        @Schema(
                description = """
                        Item 25 — active LLM provider name (e.g. `anthropic`, `openai`, `gemini`).
                        Populated only when the LLM was actually called (rank / compare path).
                        Null on the FAST rankMode path (Item 23), the stub fallback, the
                        clarify / no_results paths, and any other branch that never reaches
                        the LLM. Vista can surface this as a debug indicator alongside the
                        high-level `provider` tag.
                        """,
                nullable = true)
        String llmProvider,
        @Schema(
                description = """
                        Item 25 — active embedding provider name (e.g. `openai`, `voyage`,
                        `self-hosted`). Populated only when embeddings were actually used for
                        candidate selection on this turn. Null when the embedding subsystem is
                        dark (provider not configured) or when the path didn't need embeddings
                        (stub fallback, browse-only catalogue).
                        """,
                nullable = true)
        String embeddingProvider
) {
    public static TurnMeta empty() {
        return new TurnMeta(null, null, null, null, null, null, null, null, null, null);
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
            @JsonProperty("staleIdsFiltered") Boolean staleIdsFiltered,
            @JsonProperty("llmProvider") String llmProvider,
            @JsonProperty("embeddingProvider") String embeddingProvider) {
        this.inventoryEmpty = inventoryEmpty;
        this.queryTooStrict = queryTooStrict;
        this.degraded = degraded;
        this.provider = provider;
        this.traceId = traceId;
        this.moderationBlocked = moderationBlocked;
        this.retryable = retryable;
        this.staleIdsFiltered = staleIdsFiltered;
        this.llmProvider = llmProvider;
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * Backwards-compatible 8-arg constructor for existing call sites that don't yet
     * populate {@code llmProvider} / {@code embeddingProvider}. New code populating those
     * fields should call the 10-arg canonical constructor directly.
     */
    public TurnMeta(
            Boolean inventoryEmpty,
            Boolean queryTooStrict,
            Boolean degraded,
            String provider,
            String traceId,
            Boolean moderationBlocked,
            Boolean retryable,
            Boolean staleIdsFiltered) {
        this(inventoryEmpty, queryTooStrict, degraded, provider, traceId,
                moderationBlocked, retryable, staleIdsFiltered, null, null);
    }
}
