package com.dreamhomes.haven.dreamai.provider;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Item 25 — verifies the scaffolded OpenAI LLM provider is reachable as a Spring bean
 * via the swap mechanism but throws {@link UnsupportedOperationException} with a TODO
 * message until v2 fills the bodies in.
 */
class OpenAiLlmRankingProviderTest {

    private final OpenAiLlmRankingProvider provider = new OpenAiLlmRankingProvider();

    @Test
    void nameReturnsOpenaiSoTurnMetaCanStampIt() {
        assertThat(provider.name()).isEqualTo("openai");
    }

    @Test
    void isAvailableFalseSoServiceFallsBackToStubUntilV2() {
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void rankListingIdsThrowsUnsupportedWithStructuredOutputsTodo() {
        assertThatThrownBy(() -> provider.rankListingIds("q", "[]", Set.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("OpenAI");
    }

    @Test
    void compareListingsThrowsUnsupportedWithStructuredOutputsTodo() {
        assertThatThrownBy(() -> provider.compareListings("intent", "[]", Set.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("OpenAI");
    }
}
