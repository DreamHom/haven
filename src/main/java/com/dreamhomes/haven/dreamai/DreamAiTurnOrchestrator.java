package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.dreamai.config.DreamAiAnthropicProperties;
import com.dreamhomes.haven.dreamai.config.DreamAiIntentClassifierProperties;
import com.dreamhomes.haven.dreamai.dto.DreamAiRankMode;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestOutcome;
import com.dreamhomes.haven.dreamai.dto.DreamAiSuggestionRequest;
import com.dreamhomes.haven.dreamai.intent.Intent;
import com.dreamhomes.haven.dreamai.intent.IntentClassification;
import com.dreamhomes.haven.dreamai.intent.IntentClassifierContext;
import com.dreamhomes.haven.dreamai.provider.LlmRankingProvider;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.dreamhomes.haven.dreamai.turn.ChipOption;
import com.dreamhomes.haven.dreamai.turn.CompareReasoning;
import com.dreamhomes.haven.dreamai.turn.DreamAiTurnKind;
import com.dreamhomes.haven.dreamai.turn.TurnBlock;
import com.dreamhomes.haven.dreamai.turn.TurnMeta;
import com.dreamhomes.haven.listing.ListingService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    static final int MAX_COMPARE_LISTINGS = 5;

    /** Item 26 sub-task B — minimum entries before we route through the compare path. */
    private static final int MIN_COMPARE_LISTINGS = 2;

    private final DreamAiService dreamAiService;
    private final ListingService listingService;
    private final DreamAiAnthropicProperties anthropicProperties;
    /**
     * Item 26 sub-task D — toggle for LLM-classified intent routing. Bound from
     * {@code HAVEN_DREAM_AI_INTENT_CLASSIFIER}; defaults to {@code true}. When false,
     * {@link #buildTurn(String, String, List, String, DreamAiRankMode, List, boolean)}
     * skips the classifier and uses the regex / length fallback path.
     */
    private final DreamAiIntentClassifierProperties intentClassifierProperties;

    public AssistantTurnV1 buildTurn(String effectivePrompt, String traceId) {
        return buildTurn(effectivePrompt, traceId, List.of(), null, null, List.of(), true);
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
        return buildTurn(effectivePrompt, traceId, priorListingIds, priorUserIntent, null, List.of(), true);
    }

    /**
     * Full-control overload — adds {@code rankMode} (Item 23), {@code compareListingIds}
     * (Item 26 sub-task B), and an {@code anonymous} flag so the orchestrator can pick the
     * cost-defending default (FAST) when no explicit mode is supplied.
     *
     * @param rankMode           explicit ranking mode from the client, or {@code null} to let
     *                           the orchestrator pick the default per {@code anonymous}.
     * @param compareListingIds  optional list of listing ids the client wants compared
     *                           directly — when size >= {@value #MIN_COMPARE_LISTINGS} the
     *                           orchestrator skips URL extraction and routes straight to the
     *                           compare path; capped at {@link #MAX_COMPARE_LISTINGS}.
     * @param anonymous          true when the caller has no JWT — anonymous traffic defaults
     *                           to {@link DreamAiRankMode#FAST} so abusive prompts can't
     *                           drain the Anthropic budget; authenticated calls default to
     *                           {@link DreamAiRankMode#SMART}.
     */
    public AssistantTurnV1 buildTurn(String effectivePrompt,
                                     String traceId,
                                     List<Long> priorListingIds,
                                     String priorUserIntent,
                                     DreamAiRankMode rankMode,
                                     List<Long> compareListingIds,
                                     boolean anonymous) {
        String p = effectivePrompt == null ? "" : effectivePrompt.trim();
        if (p.isEmpty()) {
            return errorTurn(traceId, "Prompt was empty.");
        }

        // 0. Item 26 sub-task B — UI compare via explicit listingIds wins over everything
        //    else. If Vista posts {compareListingIds:[17,42]}, route straight to compare
        //    without needing URL extraction or comparison-intent heuristics.
        if (compareListingIds != null && compareListingIds.size() >= MIN_COMPARE_LISTINGS) {
            List<Long> capped = compareListingIds.stream()
                    .distinct()
                    .limit(MAX_COMPARE_LISTINGS)
                    .toList();
            return compareTurn(capped, joinIntent(priorUserIntent, p), traceId);
        }

        // 1. URL-triggered compare — explicit user intent via /listings/N pastes.
        //    Deterministic, no LLM cost — always wins before the classifier runs.
        List<Long> compareIds = extractListingIdsFromUrls(p);
        if (compareIds != null && compareIds.size() >= MIN_COMPARE_LISTINGS) {
            return compareTurn(compareIds, joinIntent(priorUserIntent, p), traceId);
        }

        // 2. Item 26 sub-task D — LLM-classified routing. Replaces the regex / length
        //    branches below when the classifier is enabled AND the active provider can
        //    answer. Any exception or unsupported provider transparently falls through
        //    to the legacy regex routing so we never lose UX continuity on a Claude blip.
        boolean hasPriorListings = priorListingIds != null && priorListingIds.size() >= MIN_COMPARE_LISTINGS;
        Intent classified = classifyIntentSafely(p, hasPriorListings);
        if (classified != null) {
            switch (classified) {
                case EMPTY:
                    return errorTurn(traceId, "Prompt was empty.");
                case COMPARE_RECENT:
                    if (hasPriorListings) {
                        List<Long> ids = priorListingIds.stream()
                                .distinct()
                                .limit(MAX_COMPARE_LISTINGS)
                                .toList();
                        return compareTurn(ids, joinIntent(priorUserIntent, p), traceId);
                    }
                    // Classifier picked COMPARE_RECENT despite no prior listings — defensive
                    // downgrade to clarify so we don't dead-end the conversation. The
                    // classifier prompt forbids this case, but we belt-and-brace it.
                    return clarifyTurn(traceId, inferProvidedConstraints(p));
                case CLARIFY:
                    return clarifyTurn(traceId, inferProvidedConstraints(p));
                case SEARCH:
                    // fall through to the rank path below
                    break;
            }
        } else {
            // Fallback path — the classifier is disabled OR not yet implemented by the
            // active provider OR threw upstream. Use the original regex / length routing.
            // 2a. Conversation-aware compare — "which of these is best?" on a chat that just
            //     showed listings.
            if (looksLikeComparisonQuestion(p) && hasPriorListings) {
                List<Long> ids = priorListingIds.stream()
                        .distinct()
                        .limit(MAX_COMPARE_LISTINGS)
                        .toList();
                return compareTurn(ids, joinIntent(priorUserIntent, p), traceId);
            }
            // 2b. Adaptive clarify — Item 26 sub-task A. Only emit chips for constraints the
            //     user has NOT already provided. When all four are detected we skip the clarify
            //     path entirely (the user already told us everything we'd ask).
            if (shouldClarify(p)) {
                Set<ConstraintKind> provided = inferProvidedConstraints(p);
                if (provided.size() < ConstraintKind.values().length) {
                    return clarifyTurn(traceId, provided);
                }
                // All constraints present — fall through to the rank path even on a short prompt.
            }
        }

        DreamAiRankMode effectiveRankMode = rankMode != null
                ? rankMode
                : (anonymous ? DreamAiRankMode.FAST : DreamAiRankMode.SMART);
        DreamAiSuggestOutcome out = dreamAiService.suggestWithOutcome(
                new DreamAiSuggestionRequest(p, null), effectiveRankMode);
        boolean stub = !anthropicProperties.hasApiKey();
        // Item 23 — when FAST mode bypassed the LLM, advertise it to the client so the
        // mode-honesty indicator (VTASK-016) can render "Quick search" instead of pretending
        // we ran the smart pipeline.
        String provider;
        if (stub) {
            provider = "stub";
        } else if (effectiveRankMode == DreamAiRankMode.FAST) {
            provider = "embeddings-only";
        } else {
            provider = "anthropic";
        }
        // Item 25 — per-call provider stamps (additive, null when the subsystem wasn't used).
        String llmProviderName = out.llmProvider();
        String embeddingProviderName = out.embeddingProvider();

        if (!out.listingIds().isEmpty()) {
            TurnMeta meta = new TurnMeta(null, null, stub, provider, traceId, null, null, null,
                    llmProviderName, embeddingProviderName);
            return new AssistantTurnV1(
                    DreamAiTurnKind.reply,
                    null,
                    List.of(TurnBlock.listings(out.listingIds())),
                    meta);
        }

        // Item 26 sub-task C — soft fallback. No exact match, but the relaxed embedding
        // search found 1-3 close-but-not-perfect listings. Render them with a markdown
        // hint so the user can opt in.
        if (out.hasBroaderMatches()) {
            TurnMeta meta = new TurnMeta(false, true, stub, provider, traceId, null, null, null,
                    llmProviderName, embeddingProviderName);
            String md = "No exact matches; here are "
                    + out.broaderMatches().size()
                    + " close options — want to see them?";
            return new AssistantTurnV1(
                    DreamAiTurnKind.reply,
                    md,
                    List.of(TurnBlock.listings(out.broaderMatches())),
                    meta);
        }

        if (out.inventoryEmpty()) {
            TurnMeta meta = new TurnMeta(true, false, stub, provider, traceId, null, null, null,
                    llmProviderName, embeddingProviderName);
            String md = "Nothing LIVE is in the catalogue yet — try again later or contact an agent.";
            return new AssistantTurnV1(DreamAiTurnKind.no_results, md, List.of(), meta);
        }

        if (out.queryTooStrict()) {
            TurnMeta meta = new TurnMeta(false, true, stub, provider, traceId, null, null, null,
                    llmProviderName, embeddingProviderName);
            String md = "Some listings were considered but none ranked high enough — relax budget, area, or filters.";
            return new AssistantTurnV1(DreamAiTurnKind.no_results, md, List.of(), meta);
        }

        TurnMeta meta = new TurnMeta(false, false, stub, provider, traceId, null, null, null,
                llmProviderName, embeddingProviderName);
        return new AssistantTurnV1(DreamAiTurnKind.no_results, "No matches right now.", List.of(), meta);
    }

    private static boolean shouldClarify(String p) {
        return p.length() < 10 && !p.matches(".*\\d.*");
    }

    /**
     * Item 26 sub-task D — runs the LLM intent classifier when enabled + available, returns
     * the classified {@link Intent}. Returns {@code null} when classification is disabled,
     * the provider isn't available, or the call fails — caller falls through to the legacy
     * regex routing on null so we never lose UX continuity on a provider blip.
     */
    private Intent classifyIntentSafely(String prompt, boolean hasPriorListings) {
        if (!intentClassifierProperties.isEnabled()) {
            return null;
        }
        LlmRankingProvider provider = dreamAiService.llmProvider();
        if (provider == null || !provider.isAvailable()) {
            return null;
        }
        try {
            IntentClassification result = provider.classifyIntent(prompt,
                    new IntentClassifierContext(false, hasPriorListings));
            return result == null ? null : result.intent();
        } catch (UnsupportedOperationException ex) {
            // Provider hasn't implemented intent classification yet (e.g. scaffolded
            // OpenAI / Gemini providers) — fall through to regex routing.
            return null;
        } catch (Exception ex) {
            log.warn("Intent classifier failed, falling back to regex routing: {}", ex.toString());
            return null;
        }
    }

    /**
     * Item 26 sub-task A — emits clarify chips only for constraints that aren't already
     * present in the prompt. The package-private signature lets unit tests verify the
     * exact chip set for a given inferred-constraints input.
     */
    static AssistantTurnV1 clarifyTurn(String traceId, Set<ConstraintKind> provided) {
        List<ChipOption> chips = new ArrayList<>();
        if (!provided.contains(ConstraintKind.BUDGET)) {
            chips.add(new ChipOption("budget", "Budget band", "My budget is under 5 million naira"));
        }
        if (!provided.contains(ConstraintKind.AREA)) {
            chips.add(new ChipOption("area", "Preferred area", "I am looking around Yaba or Surulere"));
        }
        if (!provided.contains(ConstraintKind.BEDROOMS)) {
            chips.add(new ChipOption("bedrooms", "Bedrooms", "I want a two bedroom apartment"));
        }
        if (!provided.contains(ConstraintKind.RENT_OR_BUY)) {
            chips.add(new ChipOption("term", "Rent or buy", "I want to rent"));
        }
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
        // Item 25 — the LLM provider name is stamped only when the compare actually reaches
        // the LLM (non-stub path AND >=2 live listings AND no upstream failure). The error /
        // stub / fallback branches leave it null so debug tooling can distinguish a
        // "LLM ran successfully" turn from a "LLM never got called" turn. We resolve the
        // name lazily so unit tests that mock {@code dreamAiService} (and therefore have
        // {@code llmProvider()} return null) don't NPE before reaching the stub branch.
        TurnMeta failureMeta = new TurnMeta(null, null, stub, "compare", traceId, null, null, null,
                null, null);
        if (live.size() < 2) {
            String md = "One or more of those listings is no longer LIVE — open each listing to confirm availability.";
            return new AssistantTurnV1(DreamAiTurnKind.error, md, List.of(), failureMeta);
        }
        // Cap to MAX_COMPARE_LISTINGS to keep latency + token cost bounded.
        if (live.size() > MAX_COMPARE_LISTINGS) {
            live = new ArrayList<>(live.subList(0, MAX_COMPARE_LISTINGS));
        }

        if (stub) {
            // Legacy stub-compare — UI can still render side-by-side, just no AI commentary.
            String md = "Compare the listings below — open each to see the full details.";
            return new AssistantTurnV1(DreamAiTurnKind.compare, md, List.of(TurnBlock.compare(live)), failureMeta);
        }

        try {
            CompareReasoning reasoning = dreamAiService.compareListings(userIntent, live);
            String llmName = dreamAiService.llmProvider() != null
                    ? dreamAiService.llmProvider().name()
                    : null;
            TurnMeta successMeta = new TurnMeta(null, null, stub, "compare", traceId, null, null, null,
                    llmName, null);
            if (reasoning == null || reasoning.perListing().isEmpty()) {
                // Model returned nothing usable — degrade to stub markdown but keep the
                // compare layout so the UI still has something to render. Stamp llmProvider
                // because we DID call it; it just didn't produce a usable answer.
                String md = "Compared the listings — see the cards below to make a final pick.";
                return new AssistantTurnV1(DreamAiTurnKind.compare, md, List.of(TurnBlock.compare(live)), successMeta);
            }
            String md = reasoning.summary();
            if (md == null || md.isBlank()) {
                md = "Compared the listings — see the per-listing notes for tradeoffs.";
            }
            return new AssistantTurnV1(
                    DreamAiTurnKind.compare,
                    md,
                    List.of(TurnBlock.compareWithReasoning(live, reasoning)),
                    successMeta);
        } catch (Exception ex) {
            log.warn("Compare LLM call failed, degrading to stub markdown: {}", ex.toString());
            String md = "Compared the listings below — open each to see full details.";
            return new AssistantTurnV1(DreamAiTurnKind.compare, md, List.of(TurnBlock.compare(live)), failureMeta);
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

    // ====================== Item 26 sub-task A — adaptive clarify ======================

    /** Constraint slots the clarify path can ask about. Drives the adaptive chip set. */
    enum ConstraintKind {
        AREA, BUDGET, BEDROOMS, RENT_OR_BUY
    }

    /**
     * Lagos / Nigeria neighbourhood + city names. Kept compact — the goal is to catch the
     * common single-word area mentions ("lekki", "yaba") so the clarify chip dropper has
     * decent precision. False positives here just lose a chip — harmless degradation.
     */
    private static final Pattern AREA_PATTERN = Pattern.compile(
            "\\b("
                    + "lekki|yaba|surulere|ikoyi|ikeja|ajah|magodo|ogudu|gbagada|maryland|"
                    + "v ?i|victoria ?island|lagos|abuja|ibadan|port[- ]?harcourt|"
                    + "ph|enugu|kano|asaba|benin|jos|warri|abeokuta|ilorin|owerri"
                    + ")\\b",
            Pattern.CASE_INSENSITIVE);

    /** "3 bedroom", "2-bed", "2bed", "two bedroom". */
    private static final Pattern BEDROOM_PATTERN = Pattern.compile(
            "\\b(?:\\d+|one|two|three|four|five|six)[- ]?(?:bed(?:room)?s?)\\b",
            Pattern.CASE_INSENSITIVE);

    /** "₦5m", "under 5 million", "N5m", "5m budget", "below 2,000,000". */
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(?:₦|n\\b|naira|under|below|less than|cheaper than|max(?:imum)?|budget|"
                    + "around|about|up to|at most|no more than)\\s*\\d|"
                    + "\\d[\\d,.]*\\s*(?:m(?:illion)?|k|thousand|naira|ngn)\\b",
            Pattern.CASE_INSENSITIVE);

    /** "for rent", "rental", "to buy", "for sale", "purchase". */
    private static final Pattern RENT_OR_BUY_PATTERN = Pattern.compile(
            "\\b(rent(?:al|ing)?|let|lease|buy(?:ing)?|sale|purchase|to own)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Item 26 sub-task A — parses {@code prompt} for the four constraint kinds the clarify
     * chips ask about. Returns the set of constraints the user has ALREADY supplied so the
     * orchestrator can drop the matching chips. Package-private for direct unit testing.
     */
    static Set<ConstraintKind> inferProvidedConstraints(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return EnumSet.noneOf(ConstraintKind.class);
        }
        Set<ConstraintKind> provided = EnumSet.noneOf(ConstraintKind.class);
        if (AREA_PATTERN.matcher(prompt).find()) {
            provided.add(ConstraintKind.AREA);
        }
        if (BEDROOM_PATTERN.matcher(prompt).find()) {
            provided.add(ConstraintKind.BEDROOMS);
        }
        if (BUDGET_PATTERN.matcher(prompt).find()) {
            provided.add(ConstraintKind.BUDGET);
        }
        if (RENT_OR_BUY_PATTERN.matcher(prompt).find()) {
            provided.add(ConstraintKind.RENT_OR_BUY);
        }
        return provided;
    }
}
