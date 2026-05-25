package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * SCAFFOLDED v2 — Google Gemini integration. Every method body throws
 * {@link UnsupportedOperationException}; class exists so the swap mechanism
 * ({@code HAVEN_DREAM_AI_LLM_PROVIDER=gemini}) is testable end-to-end before the
 * real implementation lands.
 *
 * <p>v2 work, per method:
 * <ul>
 *   <li>{@link #rankListingIds(String, String, Set)} → {@code POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}
 *       with {@code generationConfig.responseMimeType: "application/json"} and a
 *       {@code responseSchema} mirroring {@code {"listingIds":[...]}}. See
 *       <a href="https://ai.google.dev/gemini-api/docs/structured-output">Gemini structured output docs</a>.</li>
 *   <li>{@link #compareListings(String, String, Set)} → same endpoint, richer schema
 *       mirroring {@code CompareReasoning}.</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * Set {@code HAVEN_DREAM_AI_LLM_PROVIDER=gemini} to pick this bean.
 */
@Component
@ConditionalOnProperty(name = "haven.dream-ai.llm-provider", havingValue = "gemini")
public class GeminiLlmRankingProvider implements LlmRankingProvider {

    public static final String PROVIDER_NAME = "gemini";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // TODO: v2 — true iff GOOGLE_API_KEY (or per-project Gemini key) is configured.
        return false;
    }

    @Override
    public List<Long> rankListingIds(String userQuery, String catalogJson, Set<Long> validIds) {
        // TODO: v2 — integrate Google Gemini.
        // 1. Acquire credentials — either an API key (simplest) or a Google Cloud
        //    service account for the Vertex AI path. See
        //    https://ai.google.dev/gemini-api/docs/api-key.
        // 2. POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent
        //    Body: { contents: [{role: "user", parts: [{text: ...}]}],
        //            generationConfig: { responseMimeType: "application/json",
        //                                 responseSchema: {...} } }
        // 3. Parse parts[].text into List<Long>; validate against validIds.
        // 4. Handle 429 + the rate-limit headers Gemini returns.
        throw new UnsupportedOperationException(
                "TODO: integrate Google Gemini — see "
                        + "https://ai.google.dev/gemini-api/docs/structured-output");
    }

    @Override
    public CompareReasoning compareListings(String userIntent, String catalogJson, Set<Long> validIds) {
        // TODO: v2 — same endpoint as rank, richer responseSchema for CompareReasoning.
        throw new UnsupportedOperationException(
                "TODO: integrate Google Gemini — see "
                        + "https://ai.google.dev/gemini-api/docs/structured-output");
    }
}
