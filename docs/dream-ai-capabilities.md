# Dream AI — Haven implementation capabilities (for downstream AI / Vista)

This document tells **what Haven already implements** so assistants and frontend teams **do not redesign shipped behaviour**. The **authoritative wire contract** is **`GET /v3/api-docs`** (springdoc) plus the **Dream AI** tag description in `OpenApiConfig` — keep OpenAPI annotations in sync when you change routes or DTOs.

---

## Implemented in Haven (do not rebuild unless product explicitly expands scope)

### Persistence and roles

- **`dream_ai_chat_messages`** stores a **JSONB `content` envelope** (`DreamAiMessageDocumentV1` — schema versioned) instead of legacy split columns.
- **`client_message_id`**: optional; **partial unique index** on `(chat_id, client_message_id)` for USER rows — idempotent replay per thread.
- **Roles**: `USER`, `ASSISTANT`, `SYSTEM`, `TOOL` (TOOL reserved for phase 2; orchestration may still encode tool hints in assistant `meta` today).
- **No rigid “assistant must have listing_ids” CHECK** — clarify / compare / error / no_results turns persist cleanly.

### API surface (MVP)

| Route | Purpose |
| --- | --- |
| `POST /api/dream-ai/suggestions` | **Synchronous JSON turn** — body `DreamAiRunTurnRequest`, response `DreamAiRunTurnResponse` (`chatId`, `traceId`, `AssistantTurnV1` `turn`, legacy `listingIds`). |
| `POST /api/dream-ai/turns/stream` | **Same orchestration + persistence** as suggestions, **`text/event-stream` (SSE)**. Events: `trace` → optional `delta` (markdown chunks) → `final` (same JSON shape as JSON POST). Terminal failures use event **`problem`** (RFC 7807 **ProblemDetail** in JSON) while HTTP stays **200** once the stream has started — clients must branch on the `problem` event, not only HTTP status. |
| `GET /api/dream-ai/chats`, `GET /api/dream-ai/chats/{id}` | Thread list and **history** with the same `AssistantTurnV1` on assistant messages. |

### Orchestration (single pipeline)

- **Clarify**: very short prompts (heuristic) → `kind=clarify` + **chips** block + markdown.
- **Compare** (now AI-backed):
  - **Trigger A — URL-explicit**: prompt contains 2–5 `/listings/N` paths → those ids are pulled out and routed to compare.
  - **Trigger B — conversation-aware**: when `chatId` is supplied AND the prior assistant turn surfaced listing ids AND the current prompt looks like a comparison question (`which is best…`, `compare these for…`, `recommend / pick / suit / fit…`), the orchestrator reuses the prior turn's ids without requiring URLs in the new prompt.
  - With `HAVEN_ANTHROPIC_API_KEY` set, the matched ids (LIVE-checked, capped at 5) flow through `AnthropicListingCompareClient`. The model returns `{recommendedListingId, summary, perListing[]}`; ids are validated against the LIVE set, recommendations outside the set are coerced to `null`. The block ships back as `TurnBlock.compare` with both the legacy `compareListingIds` AND a populated `compareReasoning` payload.
  - Without an Anthropic key (or on model failure), the block carries the legacy stub markdown + `compareListingIds` only — `compareReasoning` is omitted.
  - On 0–1 LIVE matches → `kind=error` with the friendly "open each listing to confirm availability" markdown (no LLM call).
- **Rank / reply**: Anthropic **Haiku** when `HAVEN_ANTHROPIC_API_KEY` is set over a **bounded LIVE catalogue**; otherwise **stub** browse (`location=` substring). Server **re-validates** listing ids.
- **Empty states** (in `turn.meta`): **`inventoryEmpty`**, **`queryTooStrict`**, plus generic no-match copy where appropriate — distinct from “inventory empty”.

### Cross-cutting

- **Rate limit**: `DreamAiRateLimitFilter` on Dream AI POSTs — **429** `application/problem+json`, `Retry-After`, type `…/dream-ai-rate-limited` (`haven.dream-ai.rate-limit.*`).
- **Moderation**: configurable substring list → **422** Problem+JSON on JSON POST; on SSE, same semantics via **`problem`** event (`status: 422`, type `…/moderation-blocked`).
- **Observability**: structured logs with **MDC** `traceId` and `dreamAiUserId` around turns; `traceId` echoed in responses and SSE `trace` event.
- **Thread read**: **rehydration** — listing blocks filtered to **LIVE** ids the user can still see; `meta.staleIdsFiltered` when ids were dropped.

### Idempotency

- Repeat **`clientMessageId`** with the same **`chatId`** (or global lookup without `chatId` for last matching user message): **same `traceId` / turn envelope**, **no duplicate USER/ASSISTANT rows**.

### Tests (what is actually exercised)

- **`DreamAiControllerTest`**, **`DreamAiTurnStreamControllerTest`**: `@WebMvcTest` — JSON envelope, SSE event order, markdown chunking, moderation `problem` event, security wiring.
- **`DreamAiChatFlowIT`**: Postgres + Kafka — persisted threads, follow-up turns, **`clientMessageId` idempotent JSON replay**, message counts.
- **Note on SSE + MockMvc async**: Spring’s **`asyncDispatch`** path does not reliably replay **`Authorization`** through the JWT filter in this app’s integration-test setup, so **full-stack SSE is not asserted in `*IT`**; use **`DreamAiTurnStreamControllerTest`** or a future **WebClient/RANDOM_PORT** test if you need container-level SSE proof.

---

## Explicitly out of scope / phase 2 (safe to ignore for MVP parity)

Do **not** implement these in Haven **unless** product re-opens the contract:

- **Token-by-token LLM streaming** from Anthropic (today’s `delta` events chunk **final markdown** for UX only).
- **TOOL role rows** / persisted multi-step tool traces (orchestrator comments describe phase 2).
- **Cancel / partial persist + patch** mid-turn.
- **Full-database semantic / vector search** (ranking is over a **bounded catalogue slice** only).
- **External moderation API** (today: simple substring hook + 422 / `problem` terminal).

---

## Config keys (reference)

- `haven.dream-ai.anthropic.*` — API key, model, timeouts, candidate caps.
- `haven.dream-ai.moderation.banned-substrings` — MVP moderation.
- `haven.dream-ai.rate-limit.*` — per-user Dream AI POST bucket.
- `haven.errors.type-base` — ProblemDetail `type` URI prefix (OpenAPI examples align with runtime).

---

## Related repo files

| Area | Entry points |
| --- | --- |
| Controllers | `DreamAiController`, `DreamAiTurnStreamController` |
| Service / persistence | `DreamAiChatService`, `DreamAiTurnOrchestrator`, `DreamAiChatMessage*` |
| DTOs / turn model | `DreamAiRunTurnRequest`, `DreamAiRunTurnResponse`, `AssistantTurnV1`, `TurnBlock`, `TurnMeta`, `DreamAiTurnKind`, `CompareReasoning`, `PerListingNote` |
| Compare client | `AnthropicListingCompareClient` — sister of `AnthropicListingSearchClient`; structured JSON-out with id-validation defence in depth |
| Infra | `DreamAiRateLimitFilter`, `DreamAiModerationService`, Flyway `V40__dream_ai_message_envelope.sql` |

When Vista bumps its frozen OpenAPI bundle, regenerate from **`GET /v3/api-docs`** and diff — see `docs/vista/integration-log.md`.
