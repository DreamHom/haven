package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.dreamai.chat.DreamAiChatService;
import com.dreamhomes.haven.dreamai.chat.dto.DreamAiChatDetailResponse;
import com.dreamhomes.haven.dreamai.chat.dto.DreamAiChatSummaryResponse;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dream-ai")
@RequiredArgsConstructor
@Tag(name = "Dream AI")
public class DreamAiController {

    private static final int MAX_PAGE_SIZE = 50;

    private final DreamAiChatService dreamAiChatService;

    @Operation(
            summary = "Run one Dream AI turn (JSON)",
            description = """
                    **MVP contract**

                    - **Request**: natural-language `prompt` and/or structured `userChoice.sendText` after a `clarify` turn; optional `chatId`; optional `clientMessageId` for idempotent replay; optional `rankMode` (Item 23) and `compareListingIds` (Item 26 sub-task B).
                    - **Response**: `DreamAiRunTurnResponse` — `traceId` (logs + SSE), versioned `turn` (`AssistantTurnV1`), and `listingIds` for backward-compatible listing rails when `turn.kind=reply`.

                    **Provider configuration (Item 25)**

                    The LLM and embedding integrations are independently swappable via env vars:

                    - `HAVEN_DREAM_AI_LLM_PROVIDER` — `anthropic` (default) | `openai` | `gemini`. Picks which `LlmRankingProvider` bean is active at boot. Only one is loaded; the others are scaffolded with `UnsupportedOperationException` until v2 fills them in.
                    - `HAVEN_DREAM_AI_EMBEDDING_PROVIDER` — `openai` (default) | `voyage` | `self-hosted`. Picks which `EmbeddingProvider` bean is active at boot.

                    Two new optional fields on `turn.meta` reflect the active provider per call:

                    - `meta.llmProvider` — name of the LLM provider that actually ran (e.g. `"anthropic"`). Null when the LLM wasn't invoked on this turn (stub fallback, FAST rankMode, clarify / no_results / inventory-empty branches).
                    - `meta.embeddingProvider` — name of the embedding provider that ran. Null when embeddings weren't consulted (stub fallback, browse-only catalogue path, embedding subsystem dark).

                    These are purely additive — `meta.provider` keeps its existing high-level semantics (`anthropic` / `stub` / `embeddings-only` / `compare`).

                    See `docs/dream-ai-providers.md` for the full provider matrix + sample env-var sets.

                    **Orchestration**

                    Routing precedence (first match wins):

                    1. `compareListingIds` with 2–5 entries → `kind=compare` directly, no URL extraction needed (Item 26 sub-task B; longer lists capped at 5).
                    2. `/listings/N` URLs in the prompt (2+ distinct ids) → `kind=compare`.
                    3. Conversation-aware compare — prompt looks like "which is best?" AND the prior assistant turn surfaced 2+ listing ids.
                    4. Short prompts where the user hasn't already supplied area / budget / bedrooms / rent-or-buy → `kind=clarify` with the chips that AREN'T already implied by the prompt (Item 26 sub-task A — e.g. "lekki" drops the Area chip).
                    5. Rank path — see `rankMode` below.

                    **rankMode** (Item 23)

                    - `SMART` (default for authenticated callers) — embed the prompt, take pgvector NN candidates that clear the cosine-distance threshold (Item 22), and ask Claude to rank them. Higher quality on constraint-heavy prompts.
                    - `FAST` (default for anonymous callers — cost defence) — skip the Claude call; return pgvector NN order capped at 20. Cheaper, weaker on constraints. `meta.provider` is `embeddings-only` so the UI can render a "quick search" indicator (VTASK-016).
                    - Clients may always override the default by sending `rankMode` explicitly.

                    **Embedding distance threshold** (Item 22)

                    The pgvector NN query enforces a cosine-distance cutoff (`haven.dream-ai.embeddings.max-distance`, default 0.5). Junk prompts like "purple elephant tap dance" produce zero candidates and the orchestrator returns `kind=no_results` with `meta.queryTooStrict=true` WITHOUT calling Claude — cuts Anthropic spend on adversarial traffic.

                    **Soft fallback on no_results** (Item 26 sub-task C)

                    When the strict pass yields nothing but a relaxed embedding lookup (threshold × 1.5) finds up to 3 close-but-not-perfect listings, the response is `kind=reply` with markdown `"No exact matches; here are N close options — want to see them?"` and the listings block carries those broader matches. Distinguished from a real `kind=reply` by the markdown prefix; `meta.queryTooStrict=true` is also set so clients can branch on it if they want softer copy.

                    **Empty / strict / inventory** semantics on `turn.meta`:
                    - `inventoryEmpty=true` — LIVE catalogue is empty (`kind=no_results`).
                    - `queryTooStrict=true` — embeddings/Claude returned nothing; either real no-match (`kind=no_results`) or paired with broader-matches reply (above).

                    **Thread read**

                    `GET /dream-ai/chats/{id}` returns the same `AssistantTurnV1` on assistant rows; listing ids in stored blocks are re-filtered to LIVE ids on read (`meta.staleIdsFiltered` when anything dropped).

                    **Phase 2**

                    Tool-role rows, partial persist + cancel, richer moderation — see `POST /dream-ai/turns/stream` notes.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Turn result — `DreamAiRunTurnResponse` with `AssistantTurnV1`."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Unknown `chatId` for this user."),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/DreamAiModerationBlocked"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/DreamAiRateLimited"),
            @ApiResponse(responseCode = "502", description = "Anthropic error or unreadable model output.")
    })
    /**
     * Open access — Vista's public /dream-ai page makes this call SSR-side without
     * a JWT, same as it does for {@code /api/listings}. Logged-in callers get chat
     * persistence + idempotent replay; anonymous callers get a one-shot turn with
     * a null {@code chatId} on the response (no history persisted).
     */
    @PostMapping("/suggestions")
    @PreAuthorize("permitAll()")
    public DreamAiRunTurnResponse suggestions(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody DreamAiRunTurnRequest request) {
        Long userId = principal == null ? null : principal.userId();
        return dreamAiChatService.runTurn(userId, request);
    }

    @Operation(summary = "List my Dream AI chats", description = "Threads you own, most recently updated first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated chat summaries."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/chats")
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT', 'ADMIN')")
    public Page<DreamAiChatSummaryResponse> listChats(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Spring Data page (max 50).")
            @PageableDefault(size = 20)
            Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            pageable = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return dreamAiChatService.listMine(principal.userId(), pageable);
    }

    @Operation(summary = "Get one Dream AI chat", description = "Messages oldest-first for a thread you own.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chat detail."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404", description = "Chat not found for this user.")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/chats/{chatId}")
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT', 'ADMIN')")
    public DreamAiChatDetailResponse getChat(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable long chatId) {
        return dreamAiChatService.getChat(principal.userId(), chatId);
    }
}
