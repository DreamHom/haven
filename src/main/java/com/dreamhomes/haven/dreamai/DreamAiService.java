package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.client.AnthropicListingSearchClient;
import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestOutcome;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionResponse;
import com.dreamhomes.haven.listing.ListingService;
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

    private final ListingService listingService;
    private final DreamAiAnthropicProperties anthropicProperties;
    private final AnthropicListingSearchClient anthropicListingSearchClient;
    private final ListingSearchEmbeddingService listingSearchEmbeddingService;
    private final ObjectMapper objectMapper;

    /**
     * When {@code HAVEN_ANTHROPIC_API_KEY} is set, ranks LIVE listings with Claude Haiku.
     * Candidate listings are chosen by **pgvector cosine nearest neighbours** over OpenAI
     * {@code text-embedding-3-small} vectors when {@code HAVEN_OPENAI_API_KEY} is set and
     * rows exist in {@code listing_search_embeddings}; otherwise the first public browse page
     * is used (legacy cap). Server validates returned ids. Without Anthropic key, uses the
     * location substring stub.
     */
    @Transactional(readOnly = true)
    public DreamAiSuggestionResponse suggest(DreamAiSuggestionRequest request) {
        return new DreamAiSuggestionResponse(suggestWithOutcome(request).listingIds());
    }

    /**
     * Same ranking as {@link #suggest} with explicit empty-state semantics for orchestration.
     */
    @Transactional(readOnly = true)
    public DreamAiSuggestOutcome suggestWithOutcome(DreamAiSuggestionRequest request) {
        String prompt = request.prompt().trim();
        if (prompt.length() > MAX_PROMPT_CHARS) {
            prompt = prompt.substring(0, MAX_PROMPT_CHARS);
        }
        if (anthropicProperties.hasApiKey()) {
            return suggestWithAnthropicOutcome(prompt);
        }
        return suggestLocationStubOutcome(prompt);
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
        return new DreamAiSuggestOutcome(ids, false, false);
    }

    private DreamAiSuggestOutcome suggestWithAnthropicOutcome(String prompt) {
        int cap = Math.min(Math.max(1, anthropicProperties.getMaxCandidates()), 150);
        java.util.LinkedHashSet<Long> candidateIds = new java.util.LinkedHashSet<>();
        if (listingSearchEmbeddingService.active()) {
            for (Long id : listingSearchEmbeddingService.nearestLiveListingIds(prompt, cap)) {
                candidateIds.add(id);
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
            return DreamAiSuggestOutcome.empty(true, false);
        }
        Set<Long> validIds = new LinkedHashSet<>();
        for (ListingWithProperty lwp : rows) {
            validIds.add(lwp.listing().getId());
        }
        String catalogJson = buildListingsArrayJson(rows);
        List<Long> ranked = anthropicListingSearchClient.rankListingIds(prompt, catalogJson, validIds);
        log.debug("Dream AI Anthropic returned {} listing ids ({} embedding candidates) for prompt length {}",
                ranked.size(), listingSearchEmbeddingService.active() ? "pgvector+" : "browse-only",
                prompt.length());
        if (ranked.isEmpty()) {
            return DreamAiSuggestOutcome.empty(false, true);
        }
        return new DreamAiSuggestOutcome(ranked, false, false);
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
            arr.add(o);
        }
        try {
            return objectMapper.writeValueAsString(arr);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize listing catalogue", e);
        }
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
