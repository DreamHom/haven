package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.listing.embedding.ListingEmbeddingProperties;
import com.dreamhomes.haven.listing.embedding.OpenAiEmbeddingsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Item 25 — verifies the active v1 embedding provider is a pure facade over the
 * existing OpenAI client. {@code name()} returns the stable telemetry id,
 * {@code isAvailable()} mirrors the OpenAI key configuration, and {@code embed}
 * delegates without reshaping the vector.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiEmbeddingProviderTest {

    @Mock
    OpenAiEmbeddingsClient client;

    ListingEmbeddingProperties properties;
    OpenAiEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        properties = new ListingEmbeddingProperties();
        provider = new OpenAiEmbeddingProvider(client, properties);
    }

    @Test
    void nameReturnsOpenaiSoTurnMetaCanStampIt() {
        assertThat(provider.name()).isEqualTo("openai");
    }

    @Test
    void isAvailableFalseWhenKeyBlank() {
        properties.setOpenaiApiKey("");
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void isAvailableTrueWhenKeyPresent() {
        properties.setOpenaiApiKey("sk-openai-test");
        assertThat(provider.isAvailable()).isTrue();
    }

    @Test
    void embedDelegatesToOpenAiClientAndReturnsVectorUnchanged() {
        float[] expected = new float[] {0.1f, 0.2f, 0.3f};
        when(client.embed("hello world")).thenReturn(expected);

        float[] result = provider.embed("hello world");

        assertThat(result).isSameAs(expected);
        verify(client).embed("hello world");
    }
}
