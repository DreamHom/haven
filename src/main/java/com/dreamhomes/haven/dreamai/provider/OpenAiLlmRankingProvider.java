package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * SCAFFOLDED v2 — OpenAI chat completion integration. Every method body throws
 * {@link UnsupportedOperationException}; the class exists so the swap mechanism
 * ({@code HAVEN_DREAM_AI_LLM_PROVIDER=openai}) is testable end-to-end before the
 * real implementation lands.
 *
 * <p>v2 work, per method:
 * <ul>
 *   <li>{@link #rankListingIds(String, String, Set)} → {@code POST https://api.openai.com/v1/chat/completions}
 *       with a {@code gpt-4o-mini} model id, structured-output JSON schema enforcing
 *       {@code {"listingIds":[...]}}. See
 *       <a href="https://platform.openai.com/docs/guides/structured-outputs">OpenAI Structured Outputs</a>.</li>
 *   <li>{@link #compareListings(String, String, Set)} → same endpoint, richer schema
 *       mirroring {@code CompareReasoning} ({@code recommendedListingId}, {@code summary},
 *       {@code perListing[]}). Use the JSON schema response format.</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * Set {@code HAVEN_DREAM_AI_LLM_PROVIDER=openai} to pick this bean over
 * {@link AnthropicLlmRankingProvider}. The swap test
 * ({@code DreamAiLlmProviderSwapTest}) asserts the conditional fires.
 */
@Component
@ConditionalOnProperty(name = "haven.dream-ai.llm-provider", havingValue = "openai")
public class OpenAiLlmRankingProvider implements LlmRankingProvider {

    public static final String PROVIDER_NAME = "openai";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // TODO: v2 — true iff the OpenAI chat-completion key is configured. Today no
        // such property exists, so we return false; DreamAiService falls back to the
        // stub path and never invokes the rank/compare methods below.
        return false;
    }

    @Override
    public List<Long> rankListingIds(String userQuery, String catalogJson, Set<Long> validIds) {
        // TODO: v2 — integrate OpenAI chat completion with structured JSON output.
        // 1. Acquire OpenAI API key (HAVEN_OPENAI_CHAT_API_KEY) — separate from the
        //    embeddings key so the two surfaces can use different accounts / quotas.
        // 2. POST https://api.openai.com/v1/chat/completions
        //    Body: { model: "gpt-4o-mini", messages: [{role: "system", ...}, {role: "user", ...}],
        //            response_format: {type: "json_schema", json_schema: {...}} }
        // 3. Parse the model's structured-output JSON into List<Long>; validate against validIds.
        // 4. Handle 429 / 5xx with bounded retry + map to DreamAiUpstreamException.
        throw new UnsupportedOperationException(
                "TODO: integrate OpenAI chat completion with structured JSON output — see "
                        + "https://platform.openai.com/docs/guides/structured-outputs");
    }

    @Override
    public CompareReasoning compareListings(String userIntent, String catalogJson, Set<Long> validIds) {
        // TODO: v2 — same endpoint as rank, different schema. Mirror the
        // CompareReasoning record shape in the json_schema response_format.
        throw new UnsupportedOperationException(
                "TODO: integrate OpenAI chat completion with structured JSON output — see "
                        + "https://platform.openai.com/docs/guides/structured-outputs");
    }
}
