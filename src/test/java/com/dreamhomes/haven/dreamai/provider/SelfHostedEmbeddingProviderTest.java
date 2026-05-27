package com.dreamhomes.haven.dreamai.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Item 25 — verifies the scaffolded self-hosted embedding provider throws with a TODO
 * pointing at the Hugging Face TEI deploy pattern until v2 fills the body in.
 */
class SelfHostedEmbeddingProviderTest {

    private final SelfHostedEmbeddingProvider provider = new SelfHostedEmbeddingProvider();

    @Test
    void nameReturnsSelfHostedSoTurnMetaCanStampIt() {
        assertThat(provider.name()).isEqualTo("self-hosted");
    }

    @Test
    void isAvailableFalseSoEmbeddingServiceFallsBackUntilV2() {
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void embedThrowsUnsupportedWithTeiTodo() {
        assertThatThrownBy(() -> provider.embed("anything"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TODO")
                .hasMessageContaining("self-hosted");
    }
}
