package com.dreamhomes.haven.listing.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sibling of {@link ListingEmbeddingPropertiesBindingTest} — proves the YAML binding flips
 * {@link ListingEmbeddingProperties#active()} on when the {@code HAVEN_OPENAI_API_KEY}
 * placeholder resolves to a non-blank value (Item 24, post-session-tasks.md).
 */
@SpringBootTest(classes = ListingEmbeddingPropertiesActiveBindingTest.Config.class)
@TestPropertySource(properties = {
        // Mirrors the env-var → property binding from application.yml. The placeholder
        // value resolves from the inline property below, simulating Railway setting
        // HAVEN_OPENAI_API_KEY in the deploy environment.
        "HAVEN_OPENAI_API_KEY=sk-test-placeholder",
        "haven.dream-ai.embeddings.openai-api-key=${HAVEN_OPENAI_API_KEY:}"
})
class ListingEmbeddingPropertiesActiveBindingTest {

    @Autowired
    ListingEmbeddingProperties properties;

    @Test
    void placeholderResolvesToActiveSubsystem() {
        assertThat(properties.getOpenaiApiKey()).isEqualTo("sk-test-placeholder");
        assertThat(properties.active()).isTrue();
    }

    @EnableConfigurationProperties(ListingEmbeddingProperties.class)
    static class Config {
    }
}
