package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.listing.embedding.ListingEmbeddingProperties;
import com.dreamhomes.haven.listing.embedding.OpenAiEmbeddingsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Active v1 {@link EmbeddingProvider} — thin facade over the existing
 * {@link OpenAiEmbeddingsClient}. Wraps it so {@code ListingSearchEmbeddingService}
 * (and any future caller) talks to the abstraction, not the OpenAI-specific client;
 * swapping to Voyage or a self-hosted embedding model is one env var
 * ({@code HAVEN_DREAM_AI_EMBEDDING_PROVIDER}) with no code changes downstream.
 *
 * <h2>Activation</h2>
 * Default (matches when {@code HAVEN_DREAM_AI_EMBEDDING_PROVIDER} is unset). Set to
 * {@code voyage} or {@code self-hosted} to pick a sister provider.
 *
 * <h2>Availability</h2>
 * Mirrors {@link ListingEmbeddingProperties#active()} — when the OpenAI embeddings key
 * is unset, the embedding subsystem reports inactive and Dream AI falls back to the
 * legacy first-page catalogue path for candidate selection.
 */
@Component
@ConditionalOnProperty(name = "haven.dream-ai.embedding-provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER_NAME = "openai";

    private final OpenAiEmbeddingsClient client;
    private final ListingEmbeddingProperties properties;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return properties.active();
    }

    @Override
    public float[] embed(String text) {
        return client.embed(text);
    }
}
