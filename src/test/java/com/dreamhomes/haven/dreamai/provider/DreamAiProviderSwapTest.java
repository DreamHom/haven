package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.dreamai.client.AnthropicListingCompareClient;
import com.dreamhomes.haven.dreamai.client.AnthropicListingSearchClient;
import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.listing.embedding.ListingEmbeddingProperties;
import com.dreamhomes.haven.listing.embedding.OpenAiEmbeddingsClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Item 25 — proves the {@code haven.dream-ai.llm-provider} +
 * {@code haven.dream-ai.embedding-provider} configs switch which provider beans are
 * active. Even the scaffolded providers (whose method bodies throw
 * {@link UnsupportedOperationException}) resolve via the conditional, so v2 only has
 * to fill the bodies in, not change any DI wiring.
 *
 * <p>This mirrors {@code VerificationProviderSwapTest} from Item 20 — same shape.</p>
 */
class DreamAiProviderSwapTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(ProvidersConfig.class);

    // ------------------------ LLM provider swap ------------------------

    @Test
    void defaultsToAnthropicLlmProviderWhenPropertyIsAbsent() {
        contextRunner.run(ctx -> {
            LlmRankingProvider active = ctx.getBean(LlmRankingProvider.class);
            assertThat(active).isInstanceOf(AnthropicLlmRankingProvider.class);
            assertThat(active.name()).isEqualTo("anthropic");
        });
    }

    @Test
    void picksOpenAiLlmProviderWhenPropertyIsOpenai() {
        contextRunner
                .withPropertyValues("haven.dream-ai.llm-provider=openai")
                .run(ctx -> {
                    LlmRankingProvider active = ctx.getBean(LlmRankingProvider.class);
                    assertThat(active).isInstanceOf(OpenAiLlmRankingProvider.class);
                    assertThat(active.name()).isEqualTo("openai");
                    // The swap mechanism wires the bean — the method body still throws
                    // until v2 fills it in. That's the contract under test.
                    assertThatThrownBy(() -> active.rankListingIds("q", "[]", Set.of()))
                            .isInstanceOf(UnsupportedOperationException.class)
                            .hasMessageContaining("OpenAI");
                });
    }

    @Test
    void picksGeminiLlmProviderWhenPropertyIsGemini() {
        contextRunner
                .withPropertyValues("haven.dream-ai.llm-provider=gemini")
                .run(ctx -> {
                    LlmRankingProvider active = ctx.getBean(LlmRankingProvider.class);
                    assertThat(active).isInstanceOf(GeminiLlmRankingProvider.class);
                    assertThat(active.name()).isEqualTo("gemini");
                    assertThatThrownBy(() -> active.compareListings("intent", "[]", Set.of()))
                            .isInstanceOf(UnsupportedOperationException.class)
                            .hasMessageContaining("Gemini");
                });
    }

    // ------------------------ Embedding provider swap ------------------------

    @Test
    void defaultsToOpenAiEmbeddingProviderWhenPropertyIsAbsent() {
        contextRunner.run(ctx -> {
            EmbeddingProvider active = ctx.getBean(EmbeddingProvider.class);
            assertThat(active).isInstanceOf(OpenAiEmbeddingProvider.class);
            assertThat(active.name()).isEqualTo("openai");
        });
    }

    @Test
    void picksVoyageEmbeddingProviderWhenPropertyIsVoyage() {
        contextRunner
                .withPropertyValues("haven.dream-ai.embedding-provider=voyage")
                .run(ctx -> {
                    EmbeddingProvider active = ctx.getBean(EmbeddingProvider.class);
                    assertThat(active).isInstanceOf(VoyageEmbeddingProvider.class);
                    assertThat(active.name()).isEqualTo("voyage");
                    assertThatThrownBy(() -> active.embed("hello"))
                            .isInstanceOf(UnsupportedOperationException.class)
                            .hasMessageContaining("Voyage");
                });
    }

    @Test
    void picksSelfHostedEmbeddingProviderWhenPropertyIsSelfHosted() {
        contextRunner
                .withPropertyValues("haven.dream-ai.embedding-provider=self-hosted")
                .run(ctx -> {
                    EmbeddingProvider active = ctx.getBean(EmbeddingProvider.class);
                    assertThat(active).isInstanceOf(SelfHostedEmbeddingProvider.class);
                    assertThat(active.name()).isEqualTo("self-hosted");
                    assertThatThrownBy(() -> active.embed("hello"))
                            .isInstanceOf(UnsupportedOperationException.class)
                            .hasMessageContaining("self-hosted");
                });
    }

    /**
     * Imports every provider impl + supplies the collaborators
     * {@link AnthropicLlmRankingProvider} and {@link OpenAiEmbeddingProvider} need at
     * construction time. The other impls have no collaborators (they're empty stubs).
     */
    @Configuration
    @Import({AnthropicLlmRankingProvider.class,
            OpenAiLlmRankingProvider.class,
            GeminiLlmRankingProvider.class,
            OpenAiEmbeddingProvider.class,
            VoyageEmbeddingProvider.class,
            SelfHostedEmbeddingProvider.class})
    static class ProvidersConfig {
        @Bean
        AnthropicListingSearchClient anthropicListingSearchClient() {
            return mock(AnthropicListingSearchClient.class);
        }

        @Bean
        AnthropicListingCompareClient anthropicListingCompareClient() {
            return mock(AnthropicListingCompareClient.class);
        }

        @Bean
        DreamAiAnthropicProperties dreamAiAnthropicProperties() {
            return new DreamAiAnthropicProperties();
        }

        @Bean
        OpenAiEmbeddingsClient openAiEmbeddingsClient() {
            return mock(OpenAiEmbeddingsClient.class);
        }

        @Bean
        ListingEmbeddingProperties listingEmbeddingProperties() {
            return new ListingEmbeddingProperties();
        }
    }
}
