package com.dreamhomes.haven.dreamai.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SCAFFOLDED v2 — self-hosted embedding model integration. For cost-conscious deploys
 * that want predictable per-query cost (a fixed VM bill instead of per-call OpenAI / Voyage
 * fees). Typical deploy pattern: a small CPU or GPU VM running Hugging Face's
 * {@code text-embeddings-inference} (TEI) container with a
 * {@code sentence-transformers/all-MiniLM-L6-v2} (384-dim) or
 * {@code BAAI/bge-base-en-v1.5} (768-dim) model.
 *
 * <p>v2 work:
 * <ul>
 *   <li>{@link #embed(String)} → {@code POST $HAVEN_SELF_HOSTED_EMBEDDING_URL/embed}
 *       with body {@code {"inputs": [text]}}; TEI returns {@code [[…]]} (a list of
 *       embeddings). Strip the outer array, take element 0, return as {@code float[]}.</li>
 *   <li>If the model returns a width other than 1536, either (a) re-vectorise the
 *       corpus, (b) reduce {@code ListingEmbeddingProperties.dimensions} to match,
 *       OR (c) project the vector to 1536 dims via a learned linear layer (rarely worth it).</li>
 *   <li>Add liveness probes — a self-hosted embed service crashing should fail-soft
 *       (degrade to browse catalogue) the same way OpenAI 5xxs do today.</li>
 *   <li>Reference: <a href="https://github.com/huggingface/text-embeddings-inference">TEI repo</a>
 *       and <a href="https://www.sbert.net/">sentence-transformers</a>.</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * Set {@code HAVEN_DREAM_AI_EMBEDDING_PROVIDER=self-hosted} to pick this bean.
 */
@Component
@ConditionalOnProperty(name = "haven.dream-ai.embedding-provider", havingValue = "self-hosted")
public class SelfHostedEmbeddingProvider implements EmbeddingProvider {

    public static final String PROVIDER_NAME = "self-hosted";

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // TODO: v2 — true iff HAVEN_SELF_HOSTED_EMBEDDING_URL is configured AND the
        // /health endpoint responded UP within the last N seconds.
        return false;
    }

    @Override
    public float[] embed(String text) {
        // TODO: v2 — integrate a self-hosted embedding model.
        // 1. Deploy a Hugging Face TEI container with the chosen model checkpoint.
        //    Reference deploy:
        //    docker run -p 8080:80 -v $PWD/data:/data \
        //      ghcr.io/huggingface/text-embeddings-inference:1.2 \
        //      --model-id BAAI/bge-base-en-v1.5
        // 2. POST $HAVEN_SELF_HOSTED_EMBEDDING_URL/embed
        //    Body: { "inputs": [text] }
        // 3. Parse response: a 2D array; take [0] as the embedding vector.
        // 4. Validate width matches ListingEmbeddingProperties.dimensions OR migrate
        //    the corpus + adjust the YAML.
        // 5. Map transport failures to IllegalStateException for fail-soft handling.
        throw new UnsupportedOperationException(
                "TODO: integrate a self-hosted embedding model (e.g. Hugging Face TEI "
                        + "with sentence-transformers) — see "
                        + "https://github.com/huggingface/text-embeddings-inference");
    }
}
