package com.dreamhomes.haven.listing.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item 24 (post-session-tasks.md): {@code application.yml} did not bind
 * {@code HAVEN_OPENAI_API_KEY} into {@code haven.dream-ai.embeddings.openai-api-key},
 * so {@link ListingEmbeddingProperties#active()} always returned {@code false} even when
 * the env var was set on Railway — the entire embedding subsystem was dead code in prod.
 *
 * <p>This test exercises the "no env var set" path: the YAML default kicks in, the api key
 * is blank, and the subsystem stays inactive (fallback to first-page catalogue). Sibling
 * {@link ListingEmbeddingPropertiesActiveBindingTest} covers the "env var set → active" path.
 */
@SpringBootTest(
        classes = ListingEmbeddingPropertiesBindingTest.Config.class,
        properties = {
                // Placeholders are mirrored from application.yml so the test fails if the
                // ${HAVEN_OPENAI_API_KEY:} binding ever drifts again.
                "haven.dream-ai.embeddings.openai-api-key=${HAVEN_OPENAI_API_KEY:}",
                "haven.dream-ai.embeddings.model=${HAVEN_OPENAI_EMBEDDING_MODEL:text-embedding-3-small}",
                "haven.dream-ai.embeddings.dimensions=${HAVEN_OPENAI_EMBEDDING_DIMENSIONS:1536}",
                "haven.dream-ai.embeddings.openai-base-url=${HAVEN_OPENAI_BASE_URL:https://api.openai.com}",
                "haven.dream-ai.embeddings.connect-timeout-ms=${HAVEN_OPENAI_CONNECT_TIMEOUT_MS:10000}",
                "haven.dream-ai.embeddings.read-timeout-ms=${HAVEN_OPENAI_READ_TIMEOUT_MS:60000}",
                "haven.dream-ai.embeddings.max-distance=${HAVEN_DREAM_AI_EMBEDDING_MAX_DISTANCE:0.5}"
        })
class ListingEmbeddingPropertiesBindingTest {

    @Autowired
    ListingEmbeddingProperties properties;

    @Test
    void defaultBindingLeavesApiKeyBlankAndSubsystemInactive() {
        // No HAVEN_OPENAI_API_KEY env var set in the JVM — the YAML default kicks in.
        assertThat(properties.getOpenaiApiKey()).isBlank();
        assertThat(properties.active()).isFalse();
        // The non-secret defaults still bind so downstream config can read them.
        assertThat(properties.getModel()).isEqualTo("text-embedding-3-small");
        assertThat(properties.getDimensions()).isEqualTo(1536);
        assertThat(properties.getOpenaiBaseUrl()).isEqualTo("https://api.openai.com");
        // Item 22 — the distance threshold also binds with its application.yml default so
        // the cost-defence cutoff is in effect out of the box.
        assertThat(properties.getMaxDistance()).isEqualTo(0.5);
    }

    @EnableConfigurationProperties(ListingEmbeddingProperties.class)
    static class Config {
    }
}
