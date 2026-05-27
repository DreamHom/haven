package com.dreamhomes.haven.dreamai.provider;

/**
 * Strategy interface for the embedding model that powers Dream AI's pgvector NN candidate
 * selection (Item 25 in {@code docs/demo-prep/post-session-tasks.md}). Sister of
 * {@link LlmRankingProvider} — same shape (name + availability + a small method surface),
 * activated via {@code @ConditionalOnProperty} so swapping providers is one env var.
 *
 * <h2>v1 vs v2</h2>
 * <p>v1 has one active implementation: {@code OpenAiEmbeddingProvider} (a thin facade
 * over the existing {@code OpenAiEmbeddingsClient}). Sister stubs
 * {@code VoyageEmbeddingProvider} and {@code SelfHostedEmbeddingProvider} throw
 * {@link UnsupportedOperationException} with TODO bodies pointing at the docs we'd
 * follow to fill them in.</p>
 *
 * <h2>Provider selection</h2>
 * <p>{@code HAVEN_DREAM_AI_EMBEDDING_PROVIDER=openai} (default) | {@code voyage} |
 * {@code self-hosted}. Each impl carries
 * {@code @ConditionalOnProperty(name = "haven.dream-ai.embedding-provider", havingValue = "...")}.</p>
 *
 * <h2>Availability semantics</h2>
 * <p>{@link #isAvailable()} reports whether the active provider has the credentials it
 * needs. When false, {@code ListingSearchEmbeddingService.active()} returns false and
 * Dream AI falls back to the browse-page catalogue for candidate selection (Item 24
 * legacy fallback). Embedding writes are also no-ops.</p>
 */
public interface EmbeddingProvider {

    /**
     * Stable provider name surfaced on {@code TurnMeta.embeddingProvider} for debugging.
     * e.g. {@code "openai"}, {@code "voyage"}, {@code "self-hosted"}.
     */
    String name();

    /**
     * True iff this provider is actually configured (credentials present). When false
     * the surrounding embedding service short-circuits its public methods and returns
     * empty results so callers can fall back cleanly.
     */
    boolean isAvailable();

    /**
     * Embed arbitrary text into a vector. Implementations may throw on transport failure;
     * the calling service catches and degrades to an empty candidate list (same fail-soft
     * semantics as today). Returns an empty array iff the provider is dark.
     */
    float[] embed(String text);
}
