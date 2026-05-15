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

                    - **Request**: natural-language `prompt` and/or structured `userChoice.sendText` after a `clarify` turn; optional `chatId`; optional `clientMessageId` for idempotent replay.
                    - **Response**: `DreamAiRunTurnResponse` — `traceId` (logs + SSE), versioned `turn` (`AssistantTurnV1`), and `listingIds` for backward-compatible listing rails when `turn.kind=reply`.

                    **Orchestration**

                    Short prompts may yield `kind=clarify` with chip blocks; two listing URLs yield `kind=compare`; ranking uses Anthropic when configured, else stub browse. Empty catalogue vs strict query vs plain no-match are distinguished in `turn.meta` (`inventoryEmpty`, `queryTooStrict`).

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
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/suggestions")
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT', 'ADMIN')")
    public DreamAiRunTurnResponse suggestions(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody DreamAiRunTurnRequest request) {
        return dreamAiChatService.runTurn(principal.userId(), request);
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
