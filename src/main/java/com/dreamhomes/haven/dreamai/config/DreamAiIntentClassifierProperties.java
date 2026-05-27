package com.dreamhomes.haven.dreamai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Item 26 sub-task D — feature flag for the LLM-based intent classifier in
 * {@code DreamAiTurnOrchestrator}.
 *
 * <p>When {@link #isEnabled()} is {@code false}, the orchestrator skips the
 * classifier call entirely and uses the legacy regex / length-based routing — so
 * a deploy can revert behaviour via env var without a redeploy of code.</p>
 */
@Data
@ConfigurationProperties(prefix = "haven.dream-ai.intent-classifier")
public class DreamAiIntentClassifierProperties {

    /**
     * Toggle for the LLM-based intent classifier. Bound from
     * {@code HAVEN_DREAM_AI_INTENT_CLASSIFIER}; default true. Set to {@code false} to
     * revert to the regex / length routing while keeping every other Dream AI surface
     * unchanged.
     */
    private boolean enabled = true;
}
