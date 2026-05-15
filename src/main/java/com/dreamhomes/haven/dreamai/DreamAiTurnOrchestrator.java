package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestOutcome;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.dreamhomes.haven.dreamai.turn.ChipOption;
import com.dreamhomes.haven.dreamai.turn.DreamAiTurnKind;
import com.dreamhomes.haven.dreamai.turn.TurnBlock;
import com.dreamhomes.haven.dreamai.turn.TurnMeta;
import com.dreamhomes.haven.listing.ListingService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Single place that maps NL prompts + inventory to a typed {@link AssistantTurnV1}.
 * <p><b>MVP</b>: clarify heuristics, URL-based compare, ranking with empty-state meta.
 * <b>Phase 2</b>: function-calling, tool rows, streamed markdown.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DreamAiTurnOrchestrator {

    private static final Pattern LISTING_ID_IN_PATH =
            Pattern.compile("(?:/listings/|listings/)(\\d+)", Pattern.CASE_INSENSITIVE);

    private final DreamAiService dreamAiService;
    private final ListingService listingService;
    private final DreamAiAnthropicProperties anthropicProperties;

    public AssistantTurnV1 buildTurn(String effectivePrompt, String traceId) {
        String p = effectivePrompt == null ? "" : effectivePrompt.trim();
        if (p.isEmpty()) {
            return errorTurn(traceId, "Prompt was empty.");
        }

        List<Long> compareIds = extractTwoListingIdsFromUrls(p);
        if (compareIds != null) {
            return compareTurn(compareIds, traceId);
        }

        if (shouldClarify(p)) {
            return clarifyTurn(traceId);
        }

        DreamAiSuggestOutcome out = dreamAiService.suggestWithOutcome(new DreamAiSuggestionRequest(p, null));
        boolean stub = !anthropicProperties.hasApiKey();
        String provider = stub ? "stub" : "anthropic";

        if (!out.listingIds().isEmpty()) {
            TurnMeta meta = new TurnMeta(null, null, stub, provider, traceId, null, null, null);
            return new AssistantTurnV1(
                    DreamAiTurnKind.reply,
                    null,
                    List.of(TurnBlock.listings(out.listingIds())),
                    meta);
        }

        if (out.inventoryEmpty()) {
            TurnMeta meta = new TurnMeta(true, false, stub, provider, traceId, null, null, null);
            String md = "Nothing LIVE is in the catalogue yet — try again later or contact an agent.";
            return new AssistantTurnV1(DreamAiTurnKind.no_results, md, List.of(), meta);
        }

        if (out.queryTooStrict()) {
            TurnMeta meta = new TurnMeta(false, true, stub, provider, traceId, null, null, null);
            String md = "Some listings were considered but none ranked high enough — relax budget, area, or filters.";
            return new AssistantTurnV1(DreamAiTurnKind.no_results, md, List.of(), meta);
        }

        TurnMeta meta = new TurnMeta(false, false, stub, provider, traceId, null, null, null);
        return new AssistantTurnV1(DreamAiTurnKind.no_results, "No matches right now.", List.of(), meta);
    }

    private static boolean shouldClarify(String p) {
        return p.length() < 10 && !p.matches(".*\\d.*");
    }

    private static AssistantTurnV1 clarifyTurn(String traceId) {
        List<ChipOption> chips = List.of(
                new ChipOption("budget", "Budget band", "My budget is under 5 million naira"),
                new ChipOption("area", "Preferred area", "I am looking around Yaba or Surulere"),
                new ChipOption("term", "Rent or buy", "I want to rent a two bedroom apartment"));
        TurnMeta meta = new TurnMeta(null, null, null, "orchestrator", traceId, null, null, null);
        String md = "A few quick choices help us search accurately — tap one or type your own details.";
        return new AssistantTurnV1(DreamAiTurnKind.clarify, md, List.of(TurnBlock.chips(chips)), meta);
    }

    private AssistantTurnV1 compareTurn(List<Long> ids, String traceId) {
        List<Long> live = listingService.liveListingIdsAmong(ids);
        boolean stub = !anthropicProperties.hasApiKey();
        TurnMeta meta = new TurnMeta(null, null, stub, "compare", traceId, null, null, null);
        if (live.size() < 2) {
            String md = "One or both listings are no longer LIVE — open each listing to confirm availability.";
            return new AssistantTurnV1(DreamAiTurnKind.error, md, List.of(), meta);
        }
        String md = "Compare the two listings below. Full compare UI: `/compare?ids=" + live.get(0) + "," + live.get(1) + "`";
        return new AssistantTurnV1(DreamAiTurnKind.compare, md, List.of(TurnBlock.compare(live)), meta);
    }

    private List<Long> extractTwoListingIdsFromUrls(String p) {
        Matcher m = LISTING_ID_IN_PATH.matcher(p);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        while (m.find() && ids.size() < 2) {
            ids.add(Long.parseLong(m.group(1)));
        }
        if (ids.size() == 2) {
            return new ArrayList<>(ids);
        }
        return null;
    }

    private static AssistantTurnV1 errorTurn(String traceId, String message) {
        TurnMeta meta = new TurnMeta(null, null, null, "none", traceId, null, true, null);
        return new AssistantTurnV1(DreamAiTurnKind.error, message, List.of(), meta);
    }
}
