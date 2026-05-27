package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiRankMode;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestOutcome;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionResponse;
import com.dreamhomes.haven.dreamai.provider.LlmRankingProvider;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.embedding.ListingEmbeddingProperties;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DreamAiServiceTest {

    @Mock
    ListingService listingService;

    @Mock
    LlmRankingProvider llmProvider;

    @Mock
    ListingSearchEmbeddingService listingSearchEmbeddingService;

    final DreamAiAnthropicProperties anthropicProperties = new DreamAiAnthropicProperties();
    final ListingEmbeddingProperties embeddingProperties = new ListingEmbeddingProperties();
    final ObjectMapper objectMapper = new ObjectMapper();

    DreamAiService service;

    @BeforeEach
    void setUp() {
        anthropicProperties.setApiKey("");
        embeddingProperties.setMaxDistance(0.5);
        // Provider name is a free-format constant for telemetry; lenient because not every
        // test path triggers the LLM call (stub path asserts the provider was never invoked).
        lenient().when(llmProvider.name()).thenReturn("anthropic");
        // Item 25 — the service stamps `meta.embeddingProvider` from this name when
        // embeddings actually ran; lenient because not every test exercises that branch.
        lenient().when(listingSearchEmbeddingService.provider()).thenReturn(NAMED_OPENAI_EMBEDDING);
        service = new DreamAiService(listingService, anthropicProperties, embeddingProperties,
                llmProvider, listingSearchEmbeddingService, objectMapper);
    }

    /** Stand-in {@link com.dreamhomes.haven.dreamai.provider.EmbeddingProvider} used purely so the service can read {@code .name()} when stamping outcomes. */
    private static final com.dreamhomes.haven.dreamai.provider.EmbeddingProvider NAMED_OPENAI_EMBEDDING =
            new com.dreamhomes.haven.dreamai.provider.EmbeddingProvider() {
                @Override public String name() { return "openai"; }
                @Override public boolean isAvailable() { return true; }
                @Override public float[] embed(String text) { return new float[0]; }
            };

    @Test
    void withoutApiKeyUsesLocationSubstringBrowse() {
        when(llmProvider.isAvailable()).thenReturn(false);
        Listing listing = org.mockito.Mockito.mock(Listing.class);
        when(listing.getId()).thenReturn(9L);
        ListingWithProperty row = new ListingWithProperty(listing, null, null);
        when(listingService.browsePublic(null, null, null, null, null, "Yaba",
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(row)));

        DreamAiSuggestionResponse out = service.suggest(new DreamAiSuggestionRequest("  Yaba  "));

        assertThat(out.listingIds()).containsExactly(9L);
        assertThat(out.chatId()).isNull();
        verify(llmProvider, never()).rankListingIds(anyString(), anyString(), any());
    }

    @Test
    void withApiKeyUsesAnthropicOverCatalogue() {
        when(llmProvider.isAvailable()).thenReturn(true);
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
        when(llmProvider.rankListingIds(eq("two bedroom yaba"), anyString(), eq(Set.of(1L))))
                .thenReturn(List.of(1L));

        DreamAiSuggestionResponse out = service.suggest(new DreamAiSuggestionRequest("two bedroom yaba"));

        assertThat(out.listingIds()).containsExactly(1L);
        assertThat(out.chatId()).isNull();
        ArgumentCaptor<String> catalog = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).rankListingIds(eq("two bedroom yaba"), catalog.capture(), eq(Set.of(1L)));
        assertThat(catalog.getValue()).contains("\"id\":1").contains("Yaba");
    }

    @Test
    void withApiKeyAndEmptyCatalogueSkipsAnthropic() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        when(listingService.browsePublic(PageRequest.of(0, 80)))
                .thenReturn(new PageImpl<>(List.of()));
        when(listingService.findLiveWithSummariesInOrder(List.of()))
                .thenReturn(List.of());

        DreamAiSuggestionResponse out = service.suggest(new DreamAiSuggestionRequest("anything"));

        assertThat(out.listingIds()).isEmpty();
        assertThat(out.chatId()).isNull();
        verify(llmProvider, never()).rankListingIds(anyString(), anyString(), any());
    }

    /** Item 17 — catalogue JSON now carries ownerVerified + propertyDocumentsVerified for ranking. */
    @Test
    void catalogJsonIncludesVerificationFields() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        anthropicProperties.setMaxCandidates(5);

        Instant verifiedAt = Instant.parse("2026-04-12T10:00:00Z");
        Listing verifiedListing = org.mockito.Mockito.mock(Listing.class);
        when(verifiedListing.getId()).thenReturn(1L);
        when(verifiedListing.getListingType()).thenReturn(ListingType.RENT);
        when(verifiedListing.getAskingPrice()).thenReturn(new BigDecimal("500000"));
        when(verifiedListing.getCurrency()).thenReturn("NGN");
        when(verifiedListing.getTitle()).thenReturn("Verified flat");
        when(verifiedListing.isPriceNegotiable()).thenReturn(false);

        PropertySummary verifiedProp = new PropertySummary(10L, PropertyType.APARTMENT, "12 Yaba St", 2, 2,
                null, verifiedAt, 6.5, 3.4);
        ListingWithProperty verifiedRow = new ListingWithProperty(verifiedListing, verifiedProp, null, verifiedAt);

        Listing unverifiedListing = org.mockito.Mockito.mock(Listing.class);
        when(unverifiedListing.getId()).thenReturn(2L);
        when(unverifiedListing.getListingType()).thenReturn(ListingType.RENT);
        when(unverifiedListing.getAskingPrice()).thenReturn(new BigDecimal("600000"));
        when(unverifiedListing.getCurrency()).thenReturn("NGN");
        when(unverifiedListing.getTitle()).thenReturn("Plain flat");
        when(unverifiedListing.isPriceNegotiable()).thenReturn(false);
        PropertySummary unverifiedProp = new PropertySummary(11L, PropertyType.APARTMENT, "1 Yaba", 2, 2,
                null, null, null, null);
        ListingWithProperty unverifiedRow = new ListingWithProperty(unverifiedListing, unverifiedProp, null, null);

        when(listingService.browsePublic(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(verifiedRow, unverifiedRow)));
        when(listingService.findLiveWithSummariesInOrder(List.of(1L, 2L)))
                .thenReturn(List.of(verifiedRow, unverifiedRow));
        when(llmProvider.rankListingIds(anyString(), anyString(), eq(Set.of(1L, 2L))))
                .thenReturn(List.of(1L, 2L));

        service.suggest(new DreamAiSuggestionRequest("verified flats in yaba"));

        ArgumentCaptor<String> catalog = ArgumentCaptor.forClass(String.class);
        verify(llmProvider).rankListingIds(anyString(), catalog.capture(), any());
        String json = catalog.getValue();
        assertThat(json).contains("\"ownerVerified\":true").contains("\"ownerVerified\":false");
        assertThat(json).contains("\"propertyDocumentsVerified\":true").contains("\"propertyDocumentsVerified\":false");
    }

    /** Item 22 — junk prompt where no embedding candidate clears the distance threshold skips Claude. */
    @Test
    void embeddingsActiveButThresholdFiltersAllCandidatesSkipsAnthropic() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        embeddingProperties.setOpenaiApiKey("sk-openai");

        when(listingSearchEmbeddingService.active()).thenReturn(true);
        when(listingSearchEmbeddingService.nearestLiveListingIds(eq("purple elephant tap dance"), anyInt(), anyDouble()))
                .thenReturn(List.of());

        DreamAiSuggestOutcome out = service.suggestWithOutcome(
                new DreamAiSuggestionRequest("purple elephant tap dance"));

        assertThat(out.listingIds()).isEmpty();
        assertThat(out.queryTooStrict()).isTrue();
        assertThat(out.inventoryEmpty()).isFalse();
        verify(llmProvider, never()).rankListingIds(anyString(), anyString(), any());
        verify(listingService, never()).browsePublic(any(PageRequest.class));
    }

    /** Item 23 — explicit FAST rank mode skips Claude and returns pgvector ids in their existing order. */
    @Test
    void fastRankModeSkipsAnthropicAndReturnsEmbeddingOrder() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        embeddingProperties.setOpenaiApiKey("sk-openai");

        when(listingSearchEmbeddingService.active()).thenReturn(true);
        when(listingSearchEmbeddingService.nearestLiveListingIds(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(7L, 3L, 9L));

        Listing l7 = org.mockito.Mockito.mock(Listing.class);
        when(l7.getId()).thenReturn(7L);
        Listing l3 = org.mockito.Mockito.mock(Listing.class);
        when(l3.getId()).thenReturn(3L);
        Listing l9 = org.mockito.Mockito.mock(Listing.class);
        when(l9.getId()).thenReturn(9L);
        when(listingService.findLiveWithSummariesInOrder(List.of(7L, 3L, 9L)))
                .thenReturn(List.of(
                        new ListingWithProperty(l7, null, null, null),
                        new ListingWithProperty(l3, null, null, null),
                        new ListingWithProperty(l9, null, null, null)));

        DreamAiSuggestOutcome out = service.suggestWithOutcome(
                new DreamAiSuggestionRequest("any prompt", null), DreamAiRankMode.FAST);

        assertThat(out.listingIds()).containsExactly(7L, 3L, 9L);
        verify(llmProvider, never()).rankListingIds(anyString(), anyString(), any());
    }

    /** Item 23 — explicit SMART rank mode still calls Claude as before. */
    @Test
    void smartRankModeCallsAnthropic() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        anthropicProperties.setMaxCandidates(5);

        Listing l1 = org.mockito.Mockito.mock(Listing.class);
        when(l1.getId()).thenReturn(1L);
        when(l1.getListingType()).thenReturn(ListingType.RENT);
        when(l1.getAskingPrice()).thenReturn(new BigDecimal("500000"));
        when(l1.getCurrency()).thenReturn("NGN");
        when(l1.isPriceNegotiable()).thenReturn(false);
        ListingWithProperty row = new ListingWithProperty(l1, null, null, null);
        when(listingService.browsePublic(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(listingService.findLiveWithSummariesInOrder(List.of(1L)))
                .thenReturn(List.of(row));
        when(llmProvider.rankListingIds(eq("two bedroom yaba"), anyString(), eq(Set.of(1L))))
                .thenReturn(List.of(1L));

        DreamAiSuggestOutcome out = service.suggestWithOutcome(
                new DreamAiSuggestionRequest("two bedroom yaba", null), DreamAiRankMode.SMART);

        assertThat(out.listingIds()).containsExactly(1L);
        verify(llmProvider).rankListingIds(anyString(), anyString(), any());
    }

    /** Item 26 sub-task C — strict prompt with no exact match runs a broader embedding search. */
    @Test
    void emptyAnthropicResultTriggersBroaderEmbeddingFallback() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        embeddingProperties.setOpenaiApiKey("sk-openai");
        embeddingProperties.setMaxDistance(0.5);
        anthropicProperties.setMaxCandidates(5);

        when(listingSearchEmbeddingService.active()).thenReturn(true);
        // Strict pass — one candidate clears the threshold.
        when(listingSearchEmbeddingService.nearestLiveListingIds(anyString(), eq(5), eq(0.5)))
                .thenReturn(List.of(1L));
        // Broader-match pass — three close-but-not-perfect candidates surface.
        when(listingSearchEmbeddingService.nearestLiveListingIds(anyString(), eq(3), eq(0.75)))
                .thenReturn(List.of(4L, 5L, 6L));

        // browsePublic is NOT called by the broader-match fallback path; the embedding pool
        // alone is the strict candidate set. The strict path WILL try to fill candidates up
        // to cap via browse — return empty so the cap fill is a no-op.
        when(listingService.browsePublic(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of()));

        Listing l1 = catalogReadyListing(1L);
        ListingWithProperty row1 = new ListingWithProperty(l1, null, null, null);
        when(listingService.findLiveWithSummariesInOrder(List.of(1L))).thenReturn(List.of(row1));

        Listing l4 = org.mockito.Mockito.mock(Listing.class);
        when(l4.getId()).thenReturn(4L);
        Listing l5 = org.mockito.Mockito.mock(Listing.class);
        when(l5.getId()).thenReturn(5L);
        Listing l6 = org.mockito.Mockito.mock(Listing.class);
        when(l6.getId()).thenReturn(6L);
        when(listingService.findLiveWithSummariesInOrder(List.of(4L, 5L, 6L)))
                .thenReturn(List.of(
                        new ListingWithProperty(l4, null, null, null),
                        new ListingWithProperty(l5, null, null, null),
                        new ListingWithProperty(l6, null, null, null)));
        when(llmProvider.rankListingIds(anyString(), anyString(), eq(Set.of(1L))))
                .thenReturn(List.of());

        DreamAiSuggestOutcome out = service.suggestWithOutcome(
                new DreamAiSuggestionRequest("strict prompt", null));

        assertThat(out.queryTooStrict()).isTrue();
        assertThat(out.broaderMatches()).containsExactly(4L, 5L, 6L);
        assertThat(out.listingIds()).isEmpty();
    }

    /** Item 25 — outcome stamps llmProvider + embeddingProvider on a real SMART rank path. */
    @Test
    void smartRankPopulatesLlmProviderAndEmbeddingProviderOnOutcome() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        embeddingProperties.setOpenaiApiKey("sk-openai");
        anthropicProperties.setMaxCandidates(5);

        when(listingSearchEmbeddingService.active()).thenReturn(true);
        when(listingSearchEmbeddingService.nearestLiveListingIds(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(1L));
        when(listingSearchEmbeddingService.provider()).thenReturn(
                new com.dreamhomes.haven.dreamai.provider.EmbeddingProvider() {
                    @Override public String name() { return "openai"; }
                    @Override public boolean isAvailable() { return true; }
                    @Override public float[] embed(String text) { return new float[0]; }
                });

        Listing l1 = catalogReadyListing(1L);
        ListingWithProperty row = new ListingWithProperty(l1, null, null, null);
        // Cap-fill via browse runs after the embedding pass adds 1 candidate (cap=5).
        // Returning the same row keeps it as a no-op (already in the LinkedHashSet).
        when(listingService.browsePublic(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(listingService.findLiveWithSummariesInOrder(List.of(1L))).thenReturn(List.of(row));
        when(llmProvider.rankListingIds(anyString(), anyString(), eq(Set.of(1L))))
                .thenReturn(List.of(1L));

        DreamAiSuggestOutcome out = service.suggestWithOutcome(
                new DreamAiSuggestionRequest("yaba flat", null), DreamAiRankMode.SMART);

        assertThat(out.listingIds()).containsExactly(1L);
        assertThat(out.llmProvider()).isEqualTo("anthropic");
        assertThat(out.embeddingProvider()).isEqualTo("openai");
    }

    /** Item 25 — stub path leaves both provider stamps null (neither subsystem consulted). */
    @Test
    void stubPathLeavesBothProviderStampsNull() {
        when(llmProvider.isAvailable()).thenReturn(false);
        Listing listing = org.mockito.Mockito.mock(Listing.class);
        when(listing.getId()).thenReturn(9L);
        when(listingService.browsePublic(null, null, null, null, null, "Yaba",
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(new ListingWithProperty(listing, null, null))));

        DreamAiSuggestOutcome out = service.suggestWithOutcome(
                new DreamAiSuggestionRequest("Yaba", null));

        assertThat(out.llmProvider()).isNull();
        assertThat(out.embeddingProvider()).isNull();
    }

    /** Item 25 — FAST mode stamps embeddingProvider only; llmProvider stays null since the LLM was skipped. */
    @Test
    void fastModeStampsEmbeddingProviderButNotLlmProvider() {
        when(llmProvider.isAvailable()).thenReturn(true);
        anthropicProperties.setApiKey("sk-ant-test");
        embeddingProperties.setOpenaiApiKey("sk-openai");

        when(listingSearchEmbeddingService.active()).thenReturn(true);
        when(listingSearchEmbeddingService.nearestLiveListingIds(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(7L));
        when(listingSearchEmbeddingService.provider()).thenReturn(
                new com.dreamhomes.haven.dreamai.provider.EmbeddingProvider() {
                    @Override public String name() { return "openai"; }
                    @Override public boolean isAvailable() { return true; }
                    @Override public float[] embed(String text) { return new float[0]; }
                });

        Listing l7 = org.mockito.Mockito.mock(Listing.class);
        when(l7.getId()).thenReturn(7L);
        when(listingService.findLiveWithSummariesInOrder(List.of(7L)))
                .thenReturn(List.of(new ListingWithProperty(l7, null, null, null)));

        DreamAiSuggestOutcome out = service.suggestWithOutcome(
                new DreamAiSuggestionRequest("any prompt", null), DreamAiRankMode.FAST);

        assertThat(out.listingIds()).containsExactly(7L);
        assertThat(out.llmProvider()).isNull();
        assertThat(out.embeddingProvider()).isEqualTo("openai");
    }

    /**
     * Mocks the minimum set of getters {@link com.dreamhomes.haven.dreamai.DreamAiService}
     * needs when serialising a candidate row into the Claude catalogue.
     */
    private Listing catalogReadyListing(long id) {
        Listing l = org.mockito.Mockito.mock(Listing.class);
        when(l.getId()).thenReturn(id);
        when(l.getListingType()).thenReturn(ListingType.RENT);
        when(l.getAskingPrice()).thenReturn(new BigDecimal("500000"));
        when(l.getCurrency()).thenReturn("NGN");
        when(l.isPriceNegotiable()).thenReturn(false);
        return l;
    }
}
