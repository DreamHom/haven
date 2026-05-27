package com.dreamhomes.haven.dreamai.intent;

/**
 * Item 26 sub-task D — minimal context the classifier needs to disambiguate intents
 * the orchestrator can't decide from the prompt alone.
 *
 * @param hasCompareIds      true when the request carried an explicit
 *                           {@code compareListingIds} field. The orchestrator already
 *                           short-circuits to compare before calling the classifier in
 *                           this case, but we pass it through so the LLM can break
 *                           ties consistently if a future call site removes that
 *                           short-circuit.
 * @param hasPriorListings   true when the prior assistant turn surfaced 2+ listing ids
 *                           — the only state in which {@link Intent#COMPARE_RECENT} is
 *                           a valid answer. When false, the model is told NOT to pick
 *                           COMPARE_RECENT in the system prompt.
 */
public record IntentClassifierContext(boolean hasCompareIds, boolean hasPriorListings) {
}
