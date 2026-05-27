package com.dreamhomes.haven.dreamai.dto;

import java.util.List;

/**
 * Ranking diagnostics for Dream AI orchestration ({@code inventoryEmpty} vs {@code queryTooStrict})
 * plus the Item 26 sub-task C "broader matches" soft-fallback list and the Item 25
 * per-call provider stamps ({@code llmProvider}, {@code embeddingProvider}) the
 * orchestrator passes through to {@code TurnMeta}.
 *
 * <p>{@code broaderMatches} is populated when the strict search returned no exact matches
 * but a relaxed embedding lookup found a few close-but-not-perfect candidates. The
 * orchestrator surfaces them via a distinct turn shape ("here are 3 close options").</p>
 *
 * <p>{@code llmProvider} / {@code embeddingProvider} are {@code null} when the
 * corresponding subsystem was NOT consulted on this turn (e.g. the LLM is null on the
 * stub and FAST paths; the embedding provider is null on the substring stub and the
 * pure browse-only path). They are populated with {@code provider.name()} when the
 * provider was actually called so {@code TurnMeta} can surface them for debugging.</p>
 */
public record DreamAiSuggestOutcome(
        List<Long> listingIds,
        boolean inventoryEmpty,
        boolean queryTooStrict,
        List<Long> broaderMatches,
        String llmProvider,
        String embeddingProvider
) {
    public DreamAiSuggestOutcome {
        broaderMatches = broaderMatches == null ? List.of() : List.copyOf(broaderMatches);
    }

    /** Back-compat 4-arg constructor — no provider stamps. */
    public DreamAiSuggestOutcome(List<Long> listingIds,
                                 boolean inventoryEmpty,
                                 boolean queryTooStrict,
                                 List<Long> broaderMatches) {
        this(listingIds, inventoryEmpty, queryTooStrict, broaderMatches, null, null);
    }

    /** Back-compat 3-arg constructor — no broader matches, no provider stamps. */
    public DreamAiSuggestOutcome(List<Long> listingIds, boolean inventoryEmpty, boolean queryTooStrict) {
        this(listingIds, inventoryEmpty, queryTooStrict, List.of(), null, null);
    }

    public static DreamAiSuggestOutcome empty(boolean inventoryEmpty, boolean queryTooStrict) {
        return new DreamAiSuggestOutcome(List.of(), inventoryEmpty, queryTooStrict, List.of(), null, null);
    }

    /**
     * Item 26 sub-task C — no exact match but a broader embedding search surfaced
     * {@code ids} that might still help. Orchestrator renders these under a soft
     * "no exact matches; here are 3 close options" prompt.
     */
    public static DreamAiSuggestOutcome broaderMatches(List<Long> ids) {
        return new DreamAiSuggestOutcome(List.of(), false, true, ids, null, null);
    }

    /**
     * Returns a copy with {@code llmProvider} / {@code embeddingProvider} stamped on
     * the outcome — used by {@code DreamAiService} to attribute the providers that ran
     * without forking every return path.
     */
    public DreamAiSuggestOutcome withProviders(String llmProvider, String embeddingProvider) {
        return new DreamAiSuggestOutcome(
                listingIds, inventoryEmpty, queryTooStrict, broaderMatches,
                llmProvider, embeddingProvider);
    }

    public boolean hasBroaderMatches() {
        return broaderMatches != null && !broaderMatches.isEmpty();
    }
}
