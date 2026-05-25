package com.dreamhomes.haven.dreamai.provider;

import com.dreamhomes.haven.dreamai.client.AnthropicIntentClassifierClient;
import com.dreamhomes.haven.dreamai.client.AnthropicListingCompareClient;
import com.dreamhomes.haven.dreamai.client.AnthropicListingSearchClient;
import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.intent.Intent;
import com.dreamhomes.haven.dreamai.intent.IntentClassification;
import com.dreamhomes.haven.dreamai.intent.IntentClassifierContext;
import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
import com.dreamhomes.haven.dreamai.turn.PerListingNote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Item 25 — verifies the active v1 LLM provider is a pure facade over the existing
 * Anthropic clients. {@code name()} returns the stable telemetry id, {@code isAvailable()}
 * mirrors the Anthropic key configuration, and {@code rankListingIds} /
 * {@code compareListings} delegate without reshaping the inputs or outputs.
 */
@ExtendWith(MockitoExtension.class)
class AnthropicLlmRankingProviderTest {

    @Mock
    AnthropicListingSearchClient searchClient;

    @Mock
    AnthropicListingCompareClient compareClient;

    @Mock
    AnthropicIntentClassifierClient intentClassifierClient;

    DreamAiAnthropicProperties properties;
    AnthropicLlmRankingProvider provider;

    @BeforeEach
    void setUp() {
        properties = new DreamAiAnthropicProperties();
        provider = new AnthropicLlmRankingProvider(searchClient, compareClient, intentClassifierClient, properties);
    }

    @Test
    void nameReturnsAnthropicSoTurnMetaCanStampIt() {
        assertThat(provider.name()).isEqualTo("anthropic");
    }

    @Test
    void isAvailableFalseWhenApiKeyBlank() {
        properties.setApiKey("");
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void isAvailableTrueWhenApiKeyPresent() {
        properties.setApiKey("sk-ant-test");
        assertThat(provider.isAvailable()).isTrue();
    }

    @Test
    void rankListingIdsDelegatesToSearchClientWithoutReshaping() {
        Set<Long> validIds = Set.of(1L, 2L);
        when(searchClient.rankListingIds("query", "[{\"id\":1}]", validIds))
                .thenReturn(List.of(2L, 1L));

        List<Long> result = provider.rankListingIds("query", "[{\"id\":1}]", validIds);

        assertThat(result).containsExactly(2L, 1L);
        verify(searchClient).rankListingIds("query", "[{\"id\":1}]", validIds);
        verifyNoInteractions(compareClient);
    }

    @Test
    void compareListingsDelegatesToCompareClientWithoutReshaping() {
        Set<Long> validIds = Set.of(5L, 6L);
        CompareReasoning expected = new CompareReasoning(5L, "go with 5",
                List.of(new PerListingNote(5L, "headline", List.of("pro"), List.of("con"), "best")));
        when(compareClient.compareListings("intent", "[]", validIds)).thenReturn(expected);

        CompareReasoning result = provider.compareListings("intent", "[]", validIds);

        assertThat(result).isSameAs(expected);
        verify(compareClient).compareListings("intent", "[]", validIds);
        verifyNoInteractions(searchClient);
    }

    @Test
    void classifyIntentDelegatesToIntentClient() {
        IntentClassifierContext ctx = new IntentClassifierContext(false, true);
        IntentClassification expected = new IntentClassification(Intent.COMPARE_RECENT, 0.91);
        when(intentClassifierClient.classifyIntent("which is best?", ctx)).thenReturn(expected);

        IntentClassification result = provider.classifyIntent("which is best?", ctx);

        assertThat(result).isSameAs(expected);
        verify(intentClassifierClient).classifyIntent("which is best?", ctx);
        verifyNoInteractions(searchClient);
        verifyNoInteractions(compareClient);
    }
}
