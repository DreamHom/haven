package com.dreamhomes.haven.dreamai.intent;

/**
 * Item 26 sub-task D — structured output of {@code LlmRankingProvider.classifyIntent}.
 *
 * @param intent     classified intent (never null — providers default to {@link Intent#SEARCH}
 *                   when the model returns junk so the orchestrator always has a routable answer)
 * @param confidence model-reported confidence in [0, 1]. Currently informational — logged
 *                   for telemetry; the orchestrator does not threshold on it because the
 *                   fall-through regex path is cheap and a low-confidence SEARCH is the
 *                   same shape as a high-confidence SEARCH.
 */
public record IntentClassification(Intent intent, double confidence) {

    public IntentClassification {
        if (intent == null) {
            throw new IllegalArgumentException("intent must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            // Clamp instead of throw — providers occasionally emit slightly out-of-range
            // values; we want the orchestrator to keep moving rather than 500.
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }
}
