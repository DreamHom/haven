package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionResponse;
import com.dreamhomes.haven.listing.ListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DreamAiService {

    private static final int MAX_RESULTS = 20;

    private final ListingService listingService;

    /**
     * Stub: treats the prompt as a location substring against the public filtered browse
     * query (same backing query as {@code GET /api/listings?location=}).
     */
    @Transactional(readOnly = true)
    public DreamAiSuggestionResponse suggest(DreamAiSuggestionRequest request) {
        String location = request.prompt().trim();
        if (location.length() > 200) {
            location = location.substring(0, 200);
        }
        var page = listingService.browsePublic(
                null, null, null, null, null, location, PageRequest.of(0, MAX_RESULTS));
        List<Long> ids = page.getContent().stream()
                .map(lwp -> lwp.listing().getId())
                .toList();
        return new DreamAiSuggestionResponse(ids);
    }
}
