package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.dreamai.intent.IntentClassification;
import com.dreamhomes.haven.dreamai.intent.IntentClassifierContext;
import com.dreamhomes.haven.dreamai.turn.CompareReasoning;

import java.util.List;
import java.util.Set;

/**
 * Strategy interface for the LLM that ranks + compares listings on the Dream AI surface
 * (Item 25 in {@code docs/demo-prep/post-session-tasks.md}). Sister abstraction to the
 * verification-automation {@code VerificationProvider} (Item 20) — same shape:
 * one {@link #name()} for telemetry, a small number of pure-function method calls per
 * use case, exactly one implementation active at boot via {@code @ConditionalOnProperty}.
 *
 * <h2>v1 vs v2</h2>
 * <p>v1 has exactly one active implementation: {@code AnthropicLlmRankingProvider} (a
 * thin facade over the existing two Anthropic clients). The sister implementations
 * ({@code OpenAiLlmRankingProvider}, {@code GeminiLlmRankingProvider}) are scaffolded
 * with TODO bodies so v2 is a config swap + filling those bodies in, not a refactor of
 * everything that depends on this interface.</p>
 *
 * <h2>Provider selection</h2>
 * <p>{@code HAVEN_DREAM_AI_LLM_PROVIDER=anthropic} (default) | {@code openai} |
 * {@code gemini}. Each impl carries
 * {@code @ConditionalOnProperty(name = "haven.dream-ai.llm-provider", havingValue = "...")}
 * so swapping providers requires no code changes anywhere downstream.</p>
 *
 * <h2>Availability + fallback</h2>
 * <p>{@link #isAvailable()} reports whether the active provider has the credentials it
 * needs to actually answer. {@code DreamAiService} consults it before invoking the LLM
 * path and falls back to the substring stub when {@code false}. This lets a deploy ship
 * with the env vars unset and still serve traffic without crashing.</p>
 */
public interface LlmRankingProvider {

    /**
     * Stable provider name surfaced on {@code TurnMeta.llmProvider} for debugging.
     * e.g. {@code "anthropic"}, {@code "openai"}, {@code "gemini"}.
     */
    String name();

    /**
     * True iff this provider is actually configured (credentials present). When false
     * {@code DreamAiService} skips the LLM path and falls back to the substring stub.
     */
    boolean isAvailable();

    /**
     * Rank candidate listing ids best-to-worst given the user query and the JSON catalogue
     * the orchestrator built. Implementations validate returned ids against {@code validIds}
     * (defence in depth) and cap at 20 entries.
     *
     * @param userQuery   trimmed natural-language query (caller-built, length-capped)
     * @param catalogJson JSON array of compact listing rows the model is allowed to rank
     * @param validIds    ids the model is allowed to return — anything else gets dropped
     */
    List<Long> rankListingIds(String userQuery, String catalogJson, Set<Long> validIds);

    /**
     * Structured compare across 2–5 listings — pros/cons per listing + a recommendation.
     *
     * @param userIntent  natural-language intent (orchestrator builds this from the prior
     *                    search prompt + the user's comparison question)
     * @param catalogJson JSON array of the listings to compare
     * @param validIds    ids the model is allowed to reference; unknown ids are dropped
     *                    and an out-of-set recommendation is forced to null
     */
    CompareReasoning compareListings(String userIntent, String catalogJson, Set<Long> validIds);

    /**
     * Item 26 sub-task D — classify a user prompt into one of the
     * {@link com.dreamhomes.haven.dreamai.intent.Intent} routing buckets so the
     * orchestrator can pick between the rank, compare-recent, clarify, and empty paths
     * without relying on brittle regex / length heuristics.
     *
     * <p>v1 only {@code AnthropicLlmRankingProvider} ships a real implementation; the
     * scaffolded OpenAI / Gemini providers throw {@link UnsupportedOperationException}
     * so the orchestrator's fallback (regex routing) takes over transparently. The
     * default body keeps the contract additive — implementations that don't override
     * are detected by the orchestrator as "not yet wired" and never get called.</p>
     *
     * @param prompt  trimmed user prompt (already length-capped + sanitised)
     * @param context flags the orchestrator already computed (prior listings, etc.)
     * @return non-null classification; throwing is reserved for upstream failures so
     *         the orchestrator can fall back to regex routing
     * @throws UnsupportedOperationException when the active provider hasn't implemented
     *         intent classification yet
     */
    default IntentClassification classifyIntent(String prompt, IntentClassifierContext context) {
        throw new UnsupportedOperationException(
                name() + " provider has no intent-classification implementation yet");
    }
}
