package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.dreamai.client.AnthropicListingCompareClient;
import com.dreamhomes.haven.dreamai.client.AnthropicListingSearchClient;
import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Active v1 {@link LlmRankingProvider} — thin facade over the existing
 * {@link AnthropicListingSearchClient} (ranking) + {@link AnthropicListingCompareClient}
 * (compare). The facade exists so {@code DreamAiService} talks to the abstraction,
 * not to two concrete clients; swapping to OpenAI / Gemini is one env var
 * ({@code HAVEN_DREAM_AI_LLM_PROVIDER}) with zero downstream code changes.
 *
 * <h2>Activation</h2>
 * Default (matches when {@code HAVEN_DREAM_AI_LLM_PROVIDER} is unset). Set the env var
 * to {@code openai} or {@code gemini} to swap in the scaffolded sister providers.
 *
 * <h2>Availability</h2>
 * Mirrors {@link DreamAiAnthropicProperties#hasApiKey()} — when the key is unset,
 * {@code DreamAiService} falls back to the substring stub before reaching this bean.
 */
@Component
@ConditionalOnProperty(name = "haven.dream-ai.llm-provider", havingValue = "anthropic", matchIfMissing = true)
@RequiredArgsConstructor
public class AnthropicLlmRankingProvider implements LlmRankingProvider {

    public static final String PROVIDER_NAME = "anthropic";

    private final AnthropicListingSearchClient searchClient;
    private final AnthropicListingCompareClient compareClient;
    private final DreamAiAnthropicProperties properties;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return properties.hasApiKey();
    }

    @Override
    public List<Long> rankListingIds(String userQuery, String catalogJson, Set<Long> validIds) {
        return searchClient.rankListingIds(userQuery, catalogJson, validIds);
    }

    @Override
    public CompareReasoning compareListings(String userIntent, String catalogJson, Set<Long> validIds) {
        return compareClient.compareListings(userIntent, catalogJson, validIds);
    }
}
