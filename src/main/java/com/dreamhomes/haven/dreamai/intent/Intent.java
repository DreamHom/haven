package com.dreamhomes.haven.dreamai.intent;

/**
 * Item 26 sub-task D — LLM-classified router output for {@code DreamAiTurnOrchestrator}.
 *
 * <p>Replaces the regex + length-based routing that lived inline in {@code buildTurn}.
 * The orchestrator still owns the deterministic checks (URL extraction, explicit
 * {@code compareListingIds}, empty prompt) — for everything else it asks the LLM to
 * pick one of these intents and routes accordingly.</p>
 *
 * <ul>
 *   <li>{@link #SEARCH} — user wants to find listings. Route to rank.</li>
 *   <li>{@link #COMPARE_RECENT} — user wants to compare listings shown in a prior
 *       turn. Only valid when {@code hasPriorListings} is true. Route to compare.</li>
 *   <li>{@link #CLARIFY} — prompt is too vague to act on; ask for more constraints.</li>
 *   <li>{@link #EMPTY} — no real content (whitespace, two characters, gibberish).
 *       Returned by the LLM AND used as the fallback when the prompt is literally
 *       blank.</li>
 * </ul>
 */
public enum Intent {
    SEARCH,
    COMPARE_RECENT,
    CLARIFY,
    EMPTY
}
