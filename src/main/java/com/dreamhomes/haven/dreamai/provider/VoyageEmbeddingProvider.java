package com.dreamhomes.haven.dreamai.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SCAFFOLDED v2 — Voyage AI integration. Voyage is Anthropic's recommended embedding
 * partner; pairing Voyage with Claude on the ranking side keeps the whole pipeline
 * single-vendor with respect to the LLM provider's preferred shape.
 *
 * <p>v2 work:
 * <ul>
 *   <li>{@link #embed(String)} → {@code POST https://api.voyageai.com/v1/embeddings}
 *       with {@code model: "voyage-3"} (general) or {@code "voyage-3-lite"} (cost-optimised).
 *       Body: {@code {input: [text], model, input_type: "query"}}. See
 *       <a href="https://docs.voyageai.com/reference/embeddings-api">Voyage Embeddings API</a>.</li>
 *   <li>Configure dimensions to match the {@code listings_search_embeddings.embedding}
 *       pgvector column width (currently 1536 — see {@code ListingEmbeddingProperties.dimensions}).
 *       Voyage's defaults differ per model so a deploy-time validation check is wise.</li>
 *   <li>Handle 429 + per-token rate limits Voyage publishes in response headers.</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * Set {@code HAVEN_DREAM_AI_EMBEDDING_PROVIDER=voyage} to pick this bean.
 */
@Component
@ConditionalOnProperty(name = "haven.dream-ai.embedding-provider", havingValue = "voyage")
public class VoyageEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER_NAME = "voyage";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // TODO: v2 — true iff HAVEN_VOYAGE_API_KEY is configured.
        return false;
    }

    @Override
    public float[] embed(String text) {
        // TODO: v2 — integrate Voyage AI (Anthropic-recommended).
        // 1. Acquire HAVEN_VOYAGE_API_KEY from secrets.
        // 2. POST https://api.voyageai.com/v1/embeddings
        //    Body: { input: [text], model: "voyage-3", input_type: "query" }
        //    Headers: Authorization: Bearer <key>
        // 3. Parse response.data[0].embedding into float[]; assert length matches
        //    ListingEmbeddingProperties.dimensions (1536) — re-vectorise the corpus
        //    if the active model returns a different width.
        // 4. Map 429 + 5xx to IllegalStateException so the calling embedding service's
        //    catch-all fail-soft path kicks in (returns empty candidate list).
        throw new UnsupportedOperationException(
                "TODO: integrate Voyage AI — see https://docs.voyageai.com/");
    }
}
