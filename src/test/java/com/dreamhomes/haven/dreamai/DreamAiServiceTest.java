package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.client.AnthropicListingSearchClient;
import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionResponse;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.embedding.ListingSearchEmbeddingService;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.property.model.PropertyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DreamAiServiceTest {

    @Mock
    ListingService listingService;

    @Mock
    AnthropicListingSearchClient anthropicListingSearchClient;

    @Mock
    ListingSearchEmbeddingService listingSearchEmbeddingService;

    final DreamAiAnthropicProperties anthropicProperties = new DreamAiAnthropicProperties();
    final ObjectMapper objectMapper = new ObjectMapper();

    DreamAiService service;

    @BeforeEach
    void setUp() {
        anthropicProperties.setApiKey("");
        service = new DreamAiService(listingService, anthropicProperties, anthropicListingSearchClient,
                org.mockito.Mockito.mock(com.dreamhomes.haven.dreamai.client.AnthropicListingCompareClient.class),
                listingSearchEmbeddingService, objectMapper);
    }

    @Test
    void withoutApiKeyUsesLocationSubstringBrowse() {
        Listing listing = org.mockito.Mockito.mock(Listing.class);
        when(listing.getId()).thenReturn(9L);
        ListingWithProperty row = new ListingWithProperty(listing, null, null);
        when(listingService.browsePublic(null, null, null, null, null, "Yaba",
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(row)));

        DreamAiSuggestionResponse out = service.suggest(new DreamAiSuggestionRequest("  Yaba  "));

        assertThat(out.listingIds()).containsExactly(9L);
        assertThat(out.chatId()).isNull();
        verify(anthropicListingSearchClient, never()).rankListingIds(anyString(), anyString(), any());
    }

    @Test
    void withApiKeyUsesAnthropicOverCatalogue() {
        anthropicProperties.setApiKey("sk-ant-test");
        anthropicProperties.setMaxCandidates(5);

        Listing l1 = org.mockito.Mockito.mock(Listing.class);
        when(l1.getId()).thenReturn(1L);
        when(l1.getListingType()).thenReturn(ListingType.RENT);
        when(l1.getAskingPrice()).thenReturn(new BigDecimal("500000"));
        when(l1.getCurrency()).thenReturn("NGN");
        when(l1.getTitle()).thenReturn("Quiet flat");
        when(l1.getHeadline()).thenReturn("Near campus");
        when(l1.getDescription()).thenReturn("Two bed");
        when(l1.isPriceNegotiable()).thenReturn(false);
        when(l1.getPetsAllowed()).thenReturn(null);
        when(l1.getUtilitiesNote()).thenReturn(null);

        PropertySummary prop = new PropertySummary(10L, PropertyType.APARTMENT, "12 Yaba St", 2, 2,
                null, null, 6.5, 3.4);
        ListingWithProperty row = new ListingWithProperty(l1, prop, null);

        when(listingService.browsePublic(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(listingService.findLiveWithSummariesInOrder(List.of(1L)))
                .thenReturn(List.of(row));
        when(anthropicListingSearchClient.rankListingIds(eq("two bedroom yaba"), anyString(), eq(Set.of(1L))))
                .thenReturn(List.of(1L));

        DreamAiSuggestionResponse out = service.suggest(new DreamAiSuggestionRequest("two bedroom yaba"));

        assertThat(out.listingIds()).containsExactly(1L);
        assertThat(out.chatId()).isNull();
        ArgumentCaptor<String> catalog = ArgumentCaptor.forClass(String.class);
        verify(anthropicListingSearchClient).rankListingIds(eq("two bedroom yaba"), catalog.capture(), eq(Set.of(1L)));
        assertThat(catalog.getValue()).contains("\"id\":1").contains("Yaba");
    }

    @Test
    void withApiKeyAndEmptyCatalogueSkipsAnthropic() {
        anthropicProperties.setApiKey("sk-ant-test");
        when(listingService.browsePublic(PageRequest.of(0, 80)))
                .thenReturn(new PageImpl<>(List.of()));
        when(listingService.findLiveWithSummariesInOrder(List.of()))
                .thenReturn(List.of());

        DreamAiSuggestionResponse out = service.suggest(new DreamAiSuggestionRequest("anything"));

        assertThat(out.listingIds()).isEmpty();
        assertThat(out.chatId()).isNull();
        verify(anthropicListingSearchClient, never()).rankListingIds(anyString(), anyString(), any());
    }
}
