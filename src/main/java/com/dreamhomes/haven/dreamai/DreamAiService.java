package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiRankMode;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestOutcome;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionResponse;
import com.dreamhomes.haven.dreamai.provider.LlmRankingProvider;
import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.embedding.ListingEmbeddingProperties;
import com.dreamhomes.haven.listing.embedding.ListingSearchEmbeddingService;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestration core for Dream AI listing discovery + compare.
 *
 * <p>Item 25 — the LLM and embedding integrations are swappable behind
 * {@link LlmRankingProvider} and the {@link ListingSearchEmbeddingService}'s
 * {@link com.dreamhomes.haven.dreamai.provider.EmbeddingProvider} respectively. The
 * provider beans pick exactly one impl at boot via {@code @ConditionalOnProperty}, so
 * this service is provider-agnostic. The active provider name is stamped on the
 * outcome ({@code DreamAiSuggestOutcome.llmProvider / embeddingProvider}) so the
 * orchestrator can surface them as {@code TurnMeta.llmProvider} / {@code .embeddingProvider}
 * for debug + Vista mode indicators.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DreamAiService {

    private static final int MAX_RESULTS = 20;
    private static final int MAX_PROMPT_CHARS = 500;
    private static final int MAX_TITLE = 160;
    private static final int MAX_HEADLINE = 160;
    private static final int MAX_DESCRIPTION = 450;
    private static final int MAX_ADDRESS = 200;
    private static final int MAX_PETS = 120;
    private static final int MAX_UTILITIES = 200;

    /** Item 26 sub-task C — multiplier applied to the strict {@code maxDistance} when running the soft-fallback embedding search. */
    private static final double BROADER_DISTANCE_MULTIPLIER = 1.5;

    /** Item 26 sub-task C — cap on the number of soft-fallback ids the orchestrator surfaces. */
    private static final int BROADER_MATCH_LIMIT = 3;

    private final ListingService listingService;
    private final DreamAiAnthropicProperties anthropicProperties;
    private final ListingEmbeddingProperties embeddingProperties;
    private final LlmRankingProvider llmProvider;
    private final ListingSearchEmbeddingService listingSearchEmbeddingService;
    private final ObjectMapper objectMapper;

    /**
     * When an LLM provider is configured + available, ranks LIVE listings via the active
     * {@link LlmRankingProvider}. Candidate listings are chosen by pgvector cosine
     * nearest neighbours over the active embedding provider's vectors when available;
     * otherwise the first public browse page is used (legacy cap). Server validates returned ids.
     * Without an active LLM provider, uses the location substring stub.
     */
    @Transactional(readOnly = true)
    public DreamAiSuggestionResponse suggest(DreamAiSuggestionRequest request) {
        return new DreamAiSuggestionResponse(suggestWithOutcome(request).listingIds());
    }

    /**
     * Same ranking as {@link #suggest} with explicit empty-state semantics for orchestration.
     *
     * <p>Equivalent to {@link #suggestWithOutcome(DreamAiSuggestionRequest, DreamAiRankMode)}
     * with {@code rankMode == SMART} — the legacy entry point used by paths that always want
     * the LLM when configured. New call sites that need cost control should prefer the
     * two-arg variant and pass {@link DreamAiRankMode#FAST} explicitly.</p>
     */
    @Transactional(readOnly = true)
    public DreamAiSuggestOutcome suggestWithOutcome(DreamAiSuggestionRequest request) {
        return suggestWithOutcome(request, DreamAiRankMode.SMART);
    }

    /**
     * Item 23 — rank-mode aware orchestration. {@code FAST} skips the LLM ranking call and
     * returns the pgvector NN order directly (cost defence); {@code SMART} runs the full
     * LLM rank (existing behaviour). Falls back to the location-substring stub when the
     * active LLM provider is unavailable regardless of mode.
     */
    @Transactional(readOnly = true)
    public DreamAiSuggestOutcome suggestWithOutcome(DreamAiSuggestionRequest request, DreamAiRankMode rankMode) {
        String prompt = request.prompt().trim();
        if (prompt.length() > MAX_PROMPT_CHARS) {
            prompt = prompt.substring(0, MAX_PROMPT_CHARS);
        }
        DreamAiRankMode effectiveMode = rankMode == null ? DreamAiRankMode.SMART : rankMode;
        if (!llmProvider.isAvailable()) {
            return suggestLocationStubOutcome(prompt);
        }
        if (effectiveMode == DreamAiRankMode.FAST) {
            return suggestEmbeddingsOnlyOutcome(prompt);
        }
        return suggestWithLlmOutcome(prompt);
    }

    private DreamAiSuggestOutcome suggestLocationStubOutcome(String location) {
        String loc = location;
        if (loc.length() > 200) {
            loc = loc.substring(0, 200);
        }
        var page = listingService.browsePublic(
                null, null, null, null, null, loc, PageRequest.of(0, MAX_RESULTS));
        if (page.getContent().isEmpty()) {
            return DreamAiSuggestOutcome.empty(true, false);
        }
        List<Long> ids = page.getContent().stream()
                .map(lwp -> lwp.listing().getId())
                .toList();
        // Stub path neither calls the LLM nor uses embeddings — providers are both null.
        return new DreamAiSuggestOutcome(ids, false, false);
    }

    /**
     * AI-backed compare across 2–5 LIVE listings. Resolves the ids to full listing+property
     * rows (filtering closed/taken-down out at the data layer), serialises them with the
     * same compact catalogue shape used by the rank flow, and asks the active LLM provider
     * for structured pros/cons + a recommendation.
     *
     * <p><b>Deliberately NOT {@code @Transactional}.</b> The orchestrator's
     * {@code compareTurn} wraps this call in {@code try/catch} so it can degrade to a
     * stub-markdown turn when the upstream 4xx/5xxs. If we ran inside a transaction
     * (whether our own or one we joined from {@code runTurn}), the upstream exception
     * would mark the surrounding transaction as rollback-only — even though the
     * orchestrator caught the exception, the eventual commit would fail with
     * {@code UnexpectedRollbackException} and the user would see a hard 500 instead of
     * the graceful "compare cards below" fallback. Standing outside the transaction
     * keeps the rollback flag clean. The internal
     * {@code listingService.findLiveWithSummariesInOrder} call has its own read-only
     * transaction, so the DB-side concerns are still correctly bounded.</p>
     *
     * @param userIntent natural-language prompt — usually the user's prior search query
     *                   plus their comparison question (orchestrator builds this from chat history)
     * @param listingIds ids to compare; expected size 2–5 (caller responsible for the bound)
     * @return reasoning with {@code recommendedListingId} + per-listing notes; never null.
     *         Returns an empty {@link CompareReasoning#perListing()} when fewer than 2 of the
     *         requested ids are still LIVE, OR when the LLM provider is not available
     *         (the orchestrator falls back to the legacy stub markdown in that case).
     */
    public CompareReasoning compareListings(String userIntent, List<Long> listingIds) {
        if (!llmProvider.isAvailable()) {
            return new CompareReasoning(null, null, List.of());
        }
        String intent = userIntent == null ? "" : userIntent.trim();
        if (intent.length() > MAX_PROMPT_CHARS) {
            intent = intent.substring(0, MAX_PROMPT_CHARS);
        }
        List<ListingWithProperty> rows = listingService.findLiveWithSummariesInOrder(listingIds);
        if (rows.size() < 2) {
            return new CompareReasoning(null, null, List.of());
        }
        Set<Long> validIds = new LinkedHashSet<>();
        for (ListingWithProperty lwp : rows) {
            validIds.add(lwp.listing().getId());
        }
        String catalogJson = buildListingsArrayJson(rows);
        CompareReasoning reasoning =
                llmProvider.compareListings(intent, catalogJson, validIds);
        log.debug("Dream AI compare via {}: {} listings, recommendedId={}, perListing={} entries",
                llmProvider.name(), rows.size(), reasoning.recommendedListingId(), reasoning.perListing().size());
        return reasoning;
    }

    private DreamAiSuggestOutcome suggestWithLlmOutcome(String prompt) {
        int cap = Math.min(Math.max(1, anthropicProperties.getMaxCandidates()), 150);
        boolean embeddingsActive = listingSearchEmbeddingService.active();
        String embeddingProviderName = embeddingsActive
                ? listingSearchEmbeddingService.provider().name()
                : null;
        java.util.LinkedHashSet<Long> candidateIds = new java.util.LinkedHashSet<>();
        if (embeddingsActive) {
            // Item 22 — strict distance threshold; junk prompts produce an empty list and we
            // bail out before paying for an LLM round trip.
            for (Long id : listingSearchEmbeddingService.nearestLiveListingIds(
                    prompt, cap, embeddingProperties.getMaxDistance())) {
                candidateIds.add(id);
            }
            if (candidateIds.isEmpty()) {
                // No embedding candidate cleared the distance threshold. Item 26 sub-task C —
                // try a relaxed search before giving up so the user sees "close options"
                // instead of a hard no_results.
                List<Long> broader = listingSearchEmbeddingService.nearestLiveListingIds(
                        prompt, BROADER_MATCH_LIMIT,
                        embeddingProperties.getMaxDistance() * BROADER_DISTANCE_MULTIPLIER);
                if (!broader.isEmpty()) {
                    return DreamAiSuggestOutcome.broaderMatches(broader)
                            .withProviders(null, embeddingProviderName);
                }
                return DreamAiSuggestOutcome.empty(false, true)
                        .withProviders(null, embeddingProviderName);
            }
        }
        if (candidateIds.size() < cap) {
            for (ListingWithProperty lwp : listingService.browsePublic(PageRequest.of(0, cap)).getContent()) {
                if (candidateIds.size() >= cap) {
                    break;
                }
                candidateIds.add(lwp.listing().getId());
            }
        }
        List<ListingWithProperty> rows = listingService.findLiveWithSummariesInOrder(new java.util.ArrayList<>(candidateIds));
        if (rows.isEmpty()) {
            return DreamAiSuggestOutcome.empty(true, false)
                    .withProviders(null, embeddingProviderName);
        }
        Set<Long> validIds = new LinkedHashSet<>();
        for (ListingWithProperty lwp : rows) {
            validIds.add(lwp.listing().getId());
        }
        String catalogJson = buildListingsArrayJson(rows);
        List<Long> ranked = llmProvider.rankListingIds(prompt, catalogJson, validIds);
        log.debug("Dream AI {} returned {} listing ids ({} embedding candidates) for prompt length {}",
                llmProvider.name(), ranked.size(), embeddingsActive ? "pgvector+" : "browse-only", prompt.length());
        if (ranked.isEmpty()) {
            // Item 26 sub-task C — soft fallback: model rejected every candidate, but maybe
            // a wider embedding net catches something the user would find acceptable.
            if (embeddingsActive) {
                List<Long> broader = listingSearchEmbeddingService.nearestLiveListingIds(
                        prompt, BROADER_MATCH_LIMIT,
                        embeddingProperties.getMaxDistance() * BROADER_DISTANCE_MULTIPLIER);
                List<Long> filtered = listingService.findLiveWithSummariesInOrder(broader).stream()
                        .map(lwp -> lwp.listing().getId())
                        .toList();
                if (!filtered.isEmpty()) {
                    return DreamAiSuggestOutcome.broaderMatches(filtered)
                            .withProviders(llmProvider.name(), embeddingProviderName);
                }
            }
            return DreamAiSuggestOutcome.empty(false, true)
                    .withProviders(llmProvider.name(), embeddingProviderName);
        }
        return new DreamAiSuggestOutcome(ranked, false, false, List.of(),
                llmProvider.name(), embeddingProviderName);
    }

    /**
     * Item 23 — FAST mode: skip the LLM ranking call entirely. Returns pgvector nearest
     * neighbours in their existing similarity order, capped at {@link #MAX_RESULTS}. When
     * embeddings are dark, falls back to the public-browse order so callers still get a
     * coherent reply instead of an empty list.
     */
    private DreamAiSuggestOutcome suggestEmbeddingsOnlyOutcome(String prompt) {
        if (listingSearchEmbeddingService.active()) {
            String embeddingProviderName = listingSearchEmbeddingService.provider().name();
            List<Long> ids = listingSearchEmbeddingService.nearestLiveListingIds(
                    prompt, MAX_RESULTS, embeddingProperties.getMaxDistance());
            if (ids.isEmpty()) {
                return DreamAiSuggestOutcome.empty(false, true)
                        .withProviders(null, embeddingProviderName);
            }
            // Confirm each id is still LIVE in case the index lags a recent state change.
            List<Long> live = listingService.findLiveWithSummariesInOrder(ids).stream()
                    .map(lwp -> lwp.listing().getId())
                    .toList();
            if (live.isEmpty()) {
                return DreamAiSuggestOutcome.empty(false, true)
                        .withProviders(null, embeddingProviderName);
            }
            return new DreamAiSuggestOutcome(live, false, false, List.of(),
                    null, embeddingProviderName);
        }
        // Embeddings dark — degrade to the public browse order so FAST mode still produces
        // something. Same shape as the stub path but skips the location-substring filter
        // since FAST callers explicitly opted out of the smart pipeline.
        List<ListingWithProperty> rows = listingService.browsePublic(
                PageRequest.of(0, MAX_RESULTS)).getContent();
        if (rows.isEmpty()) {
            return DreamAiSuggestOutcome.empty(true, false);
        }
        return new DreamAiSuggestOutcome(rows.stream().map(lwp -> lwp.listing().getId()).toList(),
                false, false);
    }

    private String buildListingsArrayJson(List<ListingWithProperty> rows) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (ListingWithProperty lwp : rows) {
            var l = lwp.listing();
            PropertySummary p = lwp.property();
            ObjectNode o = objectMapper.createObjectNode();
            o.put("id", l.getId());
            o.put("type", l.getListingType().name());
            o.put("price", l.getAskingPrice());
            o.put("currency", l.getCurrency() != null ? l.getCurrency() : "NGN");
            putNullableText(o, "title", truncate(l.getTitle(), MAX_TITLE));
            putNullableText(o, "headline", truncate(l.getHeadline(), MAX_HEADLINE));
            putNullableText(o, "description", truncate(l.getDescription(), MAX_DESCRIPTION));
            if (p != null) {
                putNullableText(o, "address", truncate(p.address(), MAX_ADDRESS));
                if (p.bedrooms() != null) {
                    o.put("bedrooms", p.bedrooms());
                } else {
                    o.putNull("bedrooms");
                }
                if (p.bathrooms() != null) {
                    o.put("bathrooms", p.bathrooms());
                } else {
                    o.putNull("bathrooms");
                }
                if (p.latitude() != null && p.longitude() != null) {
                    o.put("geo", p.latitude() + "," + p.longitude());
                } else {
                    o.putNull("geo");
                }
            } else {
                o.putNull("address");
                o.putNull("bedrooms");
                o.putNull("bathrooms");
                o.putNull("geo");
            }
            o.put("priceNegotiable", l.isPriceNegotiable());
            putNullableText(o, "petsAllowed", truncate(l.getPetsAllowed(), MAX_PETS));
            putNullableText(o, "utilitiesNote", truncate(l.getUtilitiesNote(), MAX_UTILITIES));
            // Item 17 — surface owner identity-verification + property documents-verification
            // status so the model can honour "verified owners" / "verified property" constraints
            // in natural-language prompts. Booleans derived from the same trust signals the
            // listing payload already carries; no extra DB round trip.
            o.put("ownerVerified", lwp.ownerIdentityVerifiedAt() != null);
            boolean propertyDocsVerified = p != null && p.documentsVerifiedAt() != null;
            o.put("propertyDocumentsVerified", propertyDocsVerified);
            arr.add(o);
        }
        try {
            return objectMapper.writeValueAsString(arr);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize listing catalogue", e);
        }
    }

    /**
     * Active LLM provider — exposed so callers that want to stamp {@code meta.llmProvider}
     * on responses (e.g. compare-only turn paths that bypass {@code suggestWithOutcome})
     * can see which vendor served the request.
     */
    public LlmRankingProvider llmProvider() {
        return llmProvider;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static void putNullableText(ObjectNode o, String field, String value) {
        if (value == null) {
            o.putNull(field);
        } else {
            o.put(field, value);
        }
    }
}
