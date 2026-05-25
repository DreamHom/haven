package com.dreamhomes.haven.dreamai.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Item 25 — verifies the scaffolded Voyage embedding provider throws with a TODO
 * pointing at Voyage's docs until v2 fills the body in.
 */
class VoyageEmbeddingProviderTest {

    private final VoyageEmbeddingProvider provider = new VoyageEmbeddingProvider();

    @Test
    void nameReturnsVoyageSoTurnMetaCanStampIt() {
        assertThat(provider.name()).isEqualTo("voyage");
    }

    @Test
    void isAvailableFalseSoEmbeddingServiceFallsBackUntilV2() {
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void embedThrowsUnsupportedWithVoyageTodo() {
        assertThatThrownBy(() -> provider.embed("anything"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("Voyage");
    }
}
