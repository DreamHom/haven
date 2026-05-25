package com.dreamhomes.haven.dreamai.provider;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Item 25 — verifies the scaffolded Gemini LLM provider throws with a TODO
 * message until v2 fills the bodies in.
 */
class GeminiLlmRankingProviderTest {

    private final GeminiLlmRankingProvider provider = new GeminiLlmRankingProvider();

    @Test
    void nameReturnsGeminiSoTurnMetaCanStampIt() {
        assertThat(provider.name()).isEqualTo("gemini");
    }

    @Test
    void isAvailableFalseSoServiceFallsBackToStubUntilV2() {
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void rankListingIdsThrowsUnsupportedWithGeminiTodo() {
        assertThatThrownBy(() -> provider.rankListingIds("q", "[]", Set.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("Gemini");
    }

    @Test
    void compareListingsThrowsUnsupportedWithGeminiTodo() {
        assertThatThrownBy(() -> provider.compareListings("intent", "[]", Set.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("Gemini");
    }
}
