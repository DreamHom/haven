package com.dreamhomes.haven.dreamai.dto;

import java.util.List;

/**
 * Ranking diagnostics for Dream AI orchestration ({@code inventoryEmpty} vs {@code queryTooStrict}).
 */
public record DreamAiSuggestOutcome(
        List<Long> listingIds,
        boolean inventoryEmpty,
        boolean queryTooStrict
) {
    public static DreamAiSuggestOutcome empty(boolean inventoryEmpty, boolean queryTooStrict) {
        return new DreamAiSuggestOutcome(List.of(), inventoryEmpty, queryTooStrict);
    }
}
