# Session 9 — Dream AI

## What it is

A chat-style assistant that lets users search and compare listings in natural language. Two endpoints: `POST /api/dream-ai/suggestions` (one-shot JSON) and `POST /api/dream-ai/turns/stream` (server-streamed chat).

## What it isn't

Not text-to-SQL. Not full-database semantic search. Not autonomous. The server always picks the outcome shape; the LLM only fills in the body for the chosen path.

## The two-stage pipeline (when fully active)

**Stage 1 — candidates by meaning, not keyword.** Every listing has a pre-computed OpenAI `text-embedding-3-small` vector stored in `listing_search_embeddings`. The user's prompt gets embedded with the same model. pgvector finds the top-N nearest by cosine similarity. Hard cap 150, default 80.

**Stage 2 — ranking by reasoning.** The candidates get bundled as a JSON catalogue and sent to Claude Haiku. Claude returns `{listingIds:[...]}` ranked best-to-worst. Server validates the returned IDs against the candidate set before returning.

## The actual production state

Important audit finding: **OpenAI embedding subsystem is dead code in production**. `ListingEmbeddingProperties` reads from `haven.dream-ai.embeddings.openai-api-key`, but `application.yml` has no such section — the env var is never bound. So pgvector NN never runs. Candidate selection falls back to "first 80 LIVE listings via browse" → sent to Claude → ranked. Item 24 in `post-session-tasks.md` covers the fix.

Anthropic IS active in production (verified via response `meta.provider === "anthropic"`). Just the embedding side is dark.

## The four outcome shapes

Every response is one of four shapes. Server-picked via regex + length, not LLM-decided.

| Outcome | When |
|---|---|
| reply | Normal search returned ranked listings |
| compare | User wants to compare 2-5 specific listings |
| clarify | Prompt too short/vague to act on — return chips |
| no_results / error | Nothing matched, or input was empty / bad |

## How the orchestrator routes

`DreamAiTurnOrchestrator.buildTurn()` runs checks top-to-bottom:

1. Empty prompt → error
2. 2+ `/listings/N` URLs in prompt → compare (URL-triggered)
3. Looks-like-comparison-question regex + prior turn had 2+ listings → compare (conversation-aware)
4. Short (<10 chars) + no digits → clarify chips
5. Otherwise → call DreamAiService → reply or no_results

The routing is pure Java regex/length checks. The LLM never decides which path to take. That's what makes the system safe even with prompt injection — an attacker can't trick the LLM into choosing a different outcome shape.

## The compare flow

Returns structured analysis, not just a list. `CompareReasoning` carries:

- `recommendedListingId` — the LLM's pick, or null if no clear winner
- `summary` — markdown explanation of the recommendation
- `perListing[]` — for each listing: pros, cons, headline, bestFor (one-line persona it fits)

Defence in depth: `recommendedListingId` is forced to null if not in the LIVE-validated set; `perListing` entries with invalid IDs get dropped. Pros/cons truncated to 80 chars, max 6 items each. Headline capped 120, bestFor capped 240.

The 2-listings minimum is checked BEFORE the LLM call (in `DreamAiService.compareListings()`). If fewer than 2 of the requested IDs are LIVE → return empty `CompareReasoning` with no LLM call. Orchestrator then falls back to friendly "open each listing to confirm availability" markdown.

## Stub mode (when no Anthropic key)

When `HAVEN_ANTHROPIC_API_KEY` is unset, `DreamAiService.suggestLocationStubOutcome()` runs:
- Takes user's entire prompt as a `loc` filter
- Calls `listingService.browsePublic(null, ..., loc, ...)` — substring match on the location field
- Returns first 20 listings whose addresses match the substring

The orchestrator wraps the result in proper response shape (markdown + chips + listings block) with `meta.provider = "stub"`. From the UI's perspective the responses look similar to Anthropic responses, just less semantically smart.

## SSE streaming variant

`POST /api/dream-ai/turns/stream` does the same orchestration + persistence as the JSON endpoint but ships as `text/event-stream`. Event order: `trace` (with traceId) → optional `delta` events (markdown chunks for UX) → terminal `final` event (same JSON envelope as the JSON POST). Terminal failures use event `problem` (RFC 7807 ProblemDetail in JSON) while HTTP stays 200 once the stream started — clients must branch on the problem event, not just HTTP status.

Note: today's `delta` events chunk the FINAL markdown for UX feel. True token-by-token streaming from Anthropic is a phase-2 item.

## Persistence + idempotency

Two tables (V39/V40):

- `dream_ai_chats` — one row per chat thread, ties to `userId`
- `dream_ai_chat_messages` — one row per turn, JSONB `content` envelope, role (USER / ASSISTANT)

`clientMessageId` is optional but has a partial unique index on `(chat_id, client_message_id) WHERE role='USER'`. So if the client retries the same `clientMessageId`, the server returns the same `traceId` and assistant turn — no duplicate rows.

## Anonymous calls

The suggest + stream endpoints are public (`@PreAuthorize("permitAll()")`). When the caller is anonymous (no JWT), `DreamAiChatService.runTurn(null, ...)` skips persistence entirely — no chat row, no message rows, no idempotent replay. Returns `chatId = null` in the response. Logged-in callers get the full persistence path.

## Sanitization

`DreamAiPromptSanitizer` (added during the audit) runs before moderation and persistence. Strips ASCII control chars (except tab and newline) and zero-width / BOM characters — the classic hidden-instruction smuggling vector for prompt injection. Mutation only, not rejection.

## Moderation

`DreamAiModerationService` runs a case-insensitive substring check against `haven.dream-ai.moderation.banned-substrings` (configurable). Default list has prompt-injection canaries ("ignore previous instructions", "system prompt", etc.). Match → 422 ProblemDetail (JSON POST) or SSE `problem` event.

## Rate limiting

`DreamAiRateLimitFilter` (Bucket4j) applies to both POST endpoints. Default 30 requests per 60 seconds, keyed per-user (JWT) or per-IP (anonymous). Empty bucket → 429 with `Retry-After` header. Bypassable by an anonymous attacker rotating IPs — see `demo-claims-audit.md` for the cost-attack analysis.
