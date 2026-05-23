package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestOutcome;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.dreamhomes.haven.dreamai.turn.ChipOption;
import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
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
 * <p><b>MVP</b>: clarify heuristics, URL- and history-triggered compare, ranking with
 * empty-state meta. Compare path now calls {@link DreamAiService#compareListings(String, List)}
 * for AI-backed pros/cons + a recommendation when the Anthropic key is configured;
 * falls back to the legacy "render side-by-side, no reasoning" markdown otherwise.</p>
 *
 * <p><b>Phase 2</b>: function-calling, tool rows, streamed markdown.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DreamAiTurnOrchestrator {

    private static final Pattern LISTING_ID_IN_PATH =
            Pattern.compile("(?:/listings/|listings/)(\\d+)", Pattern.CASE_INSENSITIVE);

    /** Cap on listings the compare path will analyse — keeps Claude cost + latency bounded. */
    private static final int MAX_COMPARE_LISTINGS = 5;

    private final DreamAiService dreamAiService;
    private final ListingService listingService;
    private final DreamAiAnthropicProperties anthropicProperties;

    public AssistantTurnV1 buildTurn(String effectivePrompt, String traceId) {
        return buildTurn(effectivePrompt, traceId, List.of(), null);
    }

    /**
     * Conversation-aware overload.
     *
     * @param effectivePrompt    the user's current prompt (already de-spaced + length-capped)
     * @param traceId            trace id stamped on every turn for log correlation
     * @param priorListingIds    listing ids surfaced on the most recent assistant turn for
     *                           the same chat — enables "which is best?" follow-ups to fall
     *                           into the compare path even without URLs in the prompt.
     *                           Caller passes {@link List#of()} (or null) when there's no chat
     *                           context (one-shot anonymous turn).
     * @param priorUserIntent    the prior user prompt (the one that produced
     *                           {@code priorListingIds}). Concatenated with the current prompt
     *                           into a richer userIntent for the compare LLM call so the
     *                           model can weight the original constraints.
     */
    public AssistantTurnV1 buildTurn(String effectivePrompt,
                                     String traceId,
                                     List<Long> priorListingIds,
                                     String priorUserIntent) {
        String p = effectivePrompt == null ? "" : effectivePrompt.trim();
        if (p.isEmpty()) {
            return errorTurn(traceId, "Prompt was empty.");
        }

        // 1. URL-triggered compare wins — explicit user intent.
        List<Long> compareIds = extractListingIdsFromUrls(p);
        if (compareIds != null && compareIds.size() >= 2) {
            return compareTurn(compareIds, joinIntent(priorUserIntent, p), traceId);
        }

        // 2. Conversation-aware compare — "which of these is best?" on a chat that just
        //    showed listings. Triggers when the prompt LOOKS like a comparison question
        //    AND the prior turn surfaced 2+ listing ids.
        if (looksLikeComparisonQuestion(p) && priorListingIds != null && priorListingIds.size() >= 2) {
            List<Long> ids = priorListingIds.stream()
                    .distinct()
                    .limit(MAX_COMPARE_LISTINGS)
                    .toList();
            return compareTurn(ids, joinIntent(priorUserIntent, p), traceId);
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

    /**
     * Compare path. Validates ids are still LIVE, then asks Claude (when configured) for
     * structured pros/cons + a recommendation. Falls back to the legacy "no reasoning"
     * markdown when Anthropic isn't wired or the model returns nothing usable.
     */
    private AssistantTurnV1 compareTurn(List<Long> ids, String userIntent, String traceId) {
        List<Long> live = listingService.liveListingIdsAmong(ids);
        boolean stub = !anthropicProperties.hasApiKey();
        TurnMeta meta = new TurnMeta(null, null, stub, "compare", traceId, null, null, null);
        if (live.size() < 2) {
            String md = "One or more of those listings is no longer LIVE — open each listing to confirm availability.";
            return new AssistantTurnV1(DreamAiTurnKind.error, md, List.of(), meta);
        }
        // Cap to MAX_COMPARE_LISTINGS to keep latency + token cost bounded.
        if (live.size() > MAX_COMPARE_LISTINGS) {
            live = new ArrayList<>(live.subList(0, MAX_COMPARE_LISTINGS));
        }

        if (stub) {
            // Legacy stub-compare — UI can still render side-by-side, just no AI commentary.
            String md = "Compare the listings below — open each to see the full details.";
            return new AssistantTurnV1(DreamAiTurnKind.compare, md, List.of(TurnBlock.compare(live)), meta);
        }

        try {
            CompareReasoning reasoning = dreamAiService.compareListings(userIntent, live);
            if (reasoning == null || reasoning.perListing().isEmpty()) {
                // Model returned nothing usable — degrade to stub markdown but keep the
                // compare layout so the UI still has something to render.
                String md = "Compared the listings — see the cards below to make a final pick.";
                return new AssistantTurnV1(DreamAiTurnKind.compare, md, List.of(TurnBlock.compare(live)), meta);
            }
            String md = reasoning.summary();
            if (md == null || md.isBlank()) {
                md = "Compared the listings — see the per-listing notes for tradeoffs.";
            }
            return new AssistantTurnV1(
                    DreamAiTurnKind.compare,
                    md,
                    List.of(TurnBlock.compareWithReasoning(live, reasoning)),
                    meta);
        } catch (Exception ex) {
            log.warn("Compare LLM call failed, degrading to stub markdown: {}", ex.toString());
            String md = "Compared the listings below — open each to see full details.";
            return new AssistantTurnV1(DreamAiTurnKind.compare, md, List.of(TurnBlock.compare(live)), meta);
        }
    }

    /**
     * Greedy URL extraction — picks up to {@link #MAX_COMPARE_LISTINGS} ids from any
     * {@code /listings/N} reference in the prompt. Returns null when fewer than 2 distinct
     * ids are present (caller falls through to clarify/rank/etc).
     */
    private List<Long> extractListingIdsFromUrls(String p) {
        Matcher m = LISTING_ID_IN_PATH.matcher(p);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        while (m.find() && ids.size() < MAX_COMPARE_LISTINGS) {
            ids.add(Long.parseLong(m.group(1)));
        }
        if (ids.size() < 2) {
            return null;
        }
        return new ArrayList<>(ids);
    }

    private static final Pattern COMPARISON_INTENT = Pattern.compile(
            "\\b(which|what|who)[^.?!]*?(best|better|right|suit(s|ed)?|fits?|recommend|pick|choose|prefer|"
                    + "compar(e|ing|ison))\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Loose heuristic — fires on prompts like "which is best for me?", "compare these for a
     * young couple", "what would suit a single mum?". Pairs with the prior-turn listingIds
     * to drive the conversation-aware compare path.
     */
    private static boolean looksLikeComparisonQuestion(String p) {
        if (p.length() < 6) {
            return false;
        }
        return COMPARISON_INTENT.matcher(p).find();
    }

    /**
     * Concatenates the prior search prompt and the current question into one userIntent
     * for the LLM. Helps the model weight the original constraints (budget, area, etc.)
     * rather than guessing from the follow-up alone.
     */
    private static String joinIntent(String prior, String current) {
        if (prior == null || prior.isBlank()) {
            return current;
        }
        return "Original search: " + prior.trim() + "\n\nFollow-up: " + current.trim();
    }

    private static AssistantTurnV1 errorTurn(String traceId, String message) {
        TurnMeta meta = new TurnMeta(null, null, null, "none", traceId, null, true, null);
        return new AssistantTurnV1(DreamAiTurnKind.error, message, List.of(), meta);
    }
}
