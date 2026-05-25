package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.DreamAiTurnOrchestrator.ConstraintKind;
import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.config.DreamAiIntentClassifierProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiRankMode;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestOutcome;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.dreamhomes.haven.dreamai.turn.DreamAiTurnKind;
import com.dreamhomes.haven.dreamai.turn.TurnBlock;
import com.dreamhomes.haven.listing.ListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behaviour tests for the orchestrator's new branches (Items 22, 23, 26 A/B/C).
 * Lower-level service plumbing is covered in {@link DreamAiServiceTest}; here we focus on
 * the routing decisions the orchestrator owns (which path fires given the prompt + flags).
 */
@ExtendWith(MockitoExtension.class)
class DreamAiTurnOrchestratorTest {

    @Mock
    DreamAiService dreamAiService;

    @Mock
    ListingService listingService;

    DreamAiAnthropicProperties anthropicProperties = new DreamAiAnthropicProperties();
    DreamAiIntentClassifierProperties intentClassifierProperties = new DreamAiIntentClassifierProperties();

    DreamAiTurnOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        anthropicProperties.setApiKey("sk-ant-test");
        // Intent classifier disabled in unit tests by default so the existing regex-routing
        // assertions stay valid. Tests that specifically exercise the classifier path flip
        // intentClassifierProperties.setEnabled(true) before constructing the orchestrator.
        intentClassifierProperties.setEnabled(false);
        orchestrator = new DreamAiTurnOrchestrator(
                dreamAiService, listingService, anthropicProperties, intentClassifierProperties);
    }

    // ----------------------- Item 26 sub-task A — adaptive chips -----------------------

    @Test
    void inferProvidedConstraints_areaOnly() {
        Set<ConstraintKind> set = DreamAiTurnOrchestrator.inferProvidedConstraints("lekki");
        assertThat(set).containsExactly(ConstraintKind.AREA);
    }

    @Test
    void inferProvidedConstraints_bedroomsOnly() {
        Set<ConstraintKind> set = DreamAiTurnOrchestrator.inferProvidedConstraints("3 bedroom apartment");
        assertThat(set).contains(ConstraintKind.BEDROOMS);
        assertThat(set).doesNotContain(ConstraintKind.AREA);
    }

    @Test
    void inferProvidedConstraints_allFour() {
        Set<ConstraintKind> set = DreamAiTurnOrchestrator.inferProvidedConstraints(
                "under ₦5m for rent in Yaba 3 bedroom");
        assertThat(set).containsExactlyInAnyOrder(
                ConstraintKind.AREA, ConstraintKind.BEDROOMS,
                ConstraintKind.BUDGET, ConstraintKind.RENT_OR_BUY);
    }

    @Test
    void inferProvidedConstraints_emptyOnGibberish() {
        Set<ConstraintKind> set = DreamAiTurnOrchestrator.inferProvidedConstraints("??");
        assertThat(set).isEmpty();
    }

    @Test
    void clarifyTurn_dropsAreaChipWhenLekkiDetected() {
        AssistantTurnV1 turn = orchestrator.buildTurn("lekki", "trace-1");
        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.clarify);
        List<String> chipIds = turn.blocks().stream()
                .flatMap(b -> b.options().stream())
                .map(c -> c.id())
                .toList();
        assertThat(chipIds).doesNotContain("area");
        assertThat(chipIds).contains("budget", "bedrooms", "term");
    }

    @Test
    void clarifyTurn_emitsAllFourChipsWhenPromptHasNoConstraints() {
        AssistantTurnV1 turn = orchestrator.buildTurn("??", "trace-1");
        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.clarify);
        List<String> chipIds = turn.blocks().stream()
                .flatMap(b -> b.options().stream())
                .map(c -> c.id())
                .toList();
        assertThat(chipIds).containsExactlyInAnyOrder("budget", "area", "bedrooms", "term");
    }

    // ----------------------- Item 23 — rankMode threading ------------------------------

    @Test
    void anonymousWithNoRankMode_defaultsToFastAndAdvertisesEmbeddingsOnlyProvider() {
        when(dreamAiService.suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.FAST)))
                .thenReturn(new DreamAiSuggestOutcome(List.of(7L), false, false));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "3 bed flat in lekki", "trace-1", List.of(), null, null, List.of(), true);

        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.reply);
        assertThat(turn.meta().provider()).isEqualTo("embeddings-only");
        verify(dreamAiService).suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.FAST));
    }

    @Test
    void authenticatedWithNoRankMode_defaultsToSmart() {
        when(dreamAiService.suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.SMART)))
                .thenReturn(new DreamAiSuggestOutcome(List.of(9L), false, false));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "3 bed flat in lekki", "trace-1", List.of(), null, null, List.of(), false);

        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.reply);
        assertThat(turn.meta().provider()).isEqualTo("anthropic");
        verify(dreamAiService).suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.SMART));
    }

    @Test
    void explicitFastFromAuthenticatedRoutesThroughFast() {
        when(dreamAiService.suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.FAST)))
                .thenReturn(new DreamAiSuggestOutcome(List.of(5L), false, false));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "3 bed flat in lekki", "trace-1", List.of(), null,
                DreamAiRankMode.FAST, List.of(), false);

        assertThat(turn.meta().provider()).isEqualTo("embeddings-only");
        verify(dreamAiService).suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.FAST));
    }

    @Test
    void explicitSmartFromAnonymousRoutesThroughSmart() {
        when(dreamAiService.suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.SMART)))
                .thenReturn(new DreamAiSuggestOutcome(List.of(5L), false, false));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "3 bed flat in lekki", "trace-1", List.of(), null,
                DreamAiRankMode.SMART, List.of(), true);

        assertThat(turn.meta().provider()).isEqualTo("anthropic");
        verify(dreamAiService).suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.SMART));
    }

    // ----------------------- Item 26 sub-task B — UI compare via ids -------------------

    @Test
    void compareListingIdsRoutesDirectlyToCompare() {
        when(listingService.liveListingIdsAmong(eq(List.of(17L, 42L))))
                .thenReturn(List.of(17L, 42L));
        when(dreamAiService.compareListings(anyString(), eq(List.of(17L, 42L))))
                .thenReturn(new com.dreamhomes.haven.dreamai.turn.CompareReasoning(
                        17L, "summary", List.of(
                                new com.dreamhomes.haven.dreamai.turn.PerListingNote(17L, "h", List.of(), List.of(), "best for"),
                                new com.dreamhomes.haven.dreamai.turn.PerListingNote(42L, "h", List.of(), List.of(), "best for"))));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "compare these for me", "trace-1", List.of(), null, null,
                List.of(17L, 42L), false);

        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.compare);
        verify(dreamAiService, never()).suggestWithOutcome(any(DreamAiSuggestionRequest.class), any());
    }

    @Test
    void singleCompareListingIdFallsThroughToRank() {
        when(dreamAiService.suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.SMART)))
                .thenReturn(new DreamAiSuggestOutcome(List.of(99L), false, false));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "show me something good", "trace-1", List.of(), null, null,
                List.of(17L), false);

        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.reply);
        verify(dreamAiService).suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.SMART));
    }

    @Test
    void compareListingIdsCappedAtMaxCompareListings() {
        when(listingService.liveListingIdsAmong(any()))
                .thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        when(dreamAiService.compareListings(anyString(), any()))
                .thenReturn(new com.dreamhomes.haven.dreamai.turn.CompareReasoning(null, "summary", List.of(
                        new com.dreamhomes.haven.dreamai.turn.PerListingNote(1L, "h", List.of(), List.of(), "x"),
                        new com.dreamhomes.haven.dreamai.turn.PerListingNote(2L, "h", List.of(), List.of(), "x"))));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "compare", "trace-1", List.of(), null, null,
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), false);

        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.compare);
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(listingService).liveListingIdsAmong(captor.capture());
        assertThat(captor.getValue()).hasSize(DreamAiTurnOrchestrator.MAX_COMPARE_LISTINGS);
    }

    // ----------------------- Item 26 sub-task C — broader matches ----------------------

    @Test
    void broaderMatchesOutcomeProducesSoftFallbackTurn() {
        when(dreamAiService.suggestWithOutcome(any(DreamAiSuggestionRequest.class), eq(DreamAiRankMode.SMART)))
                .thenReturn(DreamAiSuggestOutcome.broaderMatches(List.of(4L, 5L, 6L)));

        AssistantTurnV1 turn = orchestrator.buildTurn(
                "3 bed flat in lekki", "trace-1", List.of(), null, null, List.of(), false);

        assertThat(turn.kind()).isEqualTo(DreamAiTurnKind.reply);
        assertThat(turn.markdown()).contains("close options");
        // Returned ids surface as a listings block so Vista can render them with the existing
        // listing-card component — no new block type needed.
        List<Long> ids = turn.blocks().stream()
                .filter(b -> "listings".equals(b.type()))
                .findFirst()
                .map(TurnBlock::listingIds)
                .orElseThrow();
        assertThat(ids).containsExactly(4L, 5L, 6L);
    }
}
