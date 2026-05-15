package com.dreamhomes.haven.dreamai;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.common.DomainException;
import com.dreamhomes.haven.dreamai.chat.DreamAiChatService;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-sent events for a Dream AI turn — same persistence and idempotency as {@code POST /suggestions},
 * with incremental {@code delta} events when {@link com.dreamhomes.haven.dreamai.turn.AssistantTurnV1#markdown()} is present.
 */
@RestController
@RequestMapping("/api/dream-ai/turns")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dream AI")
public class DreamAiTurnStreamController {

    private static final long SSE_TIMEOUT_MS = 300_000L;
    private static final int MARKDOWN_CHUNK = 96;
    private static final Executor STREAM_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final DreamAiChatService dreamAiChatService;

    @Value("${haven.errors.type-base:https://github.com/DreamHom/haven/blob/main/docs/errors/}")
    private String errorTypeBase;

    @Operation(
            summary = "Run a Dream AI turn (SSE)",
            description = """
                    Same orchestration and persistence as `POST /dream-ai/suggestions`, delivered as **SSE** (`text/event-stream`).

                    **Events (MVP)**

                    - `trace` — JSON `{"traceId":"<uuid>"}` — correlate with logs and support.
                    - `delta` — JSON `{"markdown":"<fragment>"}` — optional chunks when the assistant turn includes markdown.
                    - `final` — JSON body matching **DreamAiRunTurnResponse** (full validated `AssistantTurnV1` + `listingIds` + `chatId` + `traceId`).

                    **Errors on stream**

                    - `problem` — one terminal **ProblemDetail** JSON (same fields as synchronous Problem+JSON). Non-success HTTP status is not used mid-stream; clients should branch on this event.

                    **Phase 2**

                    True token-level streaming from the model, tool-step events, and cancellation — not wire-stable yet.

                    **Idempotency**

                    Reuse `clientMessageId` with the same `chatId` (or global replay without `chatId`) as the JSON POST — the stream will emit the same `final` envelope without re-running tools.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = """
                            **SSE body** — see summary for `trace` / `delta` / `final` / `problem` events.

                            HTTP is almost always **200** once the stream starts. **Domain failures** (e.g. bad prompt, \
                            chat not found, moderation) are delivered as a terminal **`problem`** event whose JSON is \
                            an RFC 7807 **ProblemDetail** (check `status` and `type` — e.g. **422** moderation-blocked). \
                            This matches the integration tests: do not expect a bare HTTP 422 on this path.
                            """),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/DreamAiRateLimited"),
            @ApiResponse(responseCode = "502", description = "Anthropic error or unreadable model output (synchronous failure before SSE opens — rare).")
    })
    // Open access — same rationale as POST /api/dream-ai/suggestions: Vista's public
    // /dream-ai page hits this SSR-side without a JWT. Anonymous calls don't persist
    // chat rows; the SSE final event still carries the full turn payload.
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("permitAll()")
    public SseEmitter streamTurn(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Same JSON body as synchronous turn POST.")
            @Valid @RequestBody DreamAiRunTurnRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Long userId = principal == null ? null : principal.userId();
        STREAM_POOL.execute(() -> deliverStream(emitter, userId, request));
        return emitter;
    }

    private void deliverStream(SseEmitter emitter, Long userId, DreamAiRunTurnRequest request) {
        try {
            DreamAiRunTurnResponse response = dreamAiChatService.runTurn(userId, request);
            sendJsonEvent(emitter, "trace", Map.of("traceId", response.traceId()));
            String md = response.turn() != null ? response.turn().markdown() : null;
            if (md != null && !md.isBlank()) {
                for (String chunk : chunkText(md, MARKDOWN_CHUNK)) {
                    sendJsonEvent(emitter, "delta", Map.of("markdown", chunk));
                }
            }
            sendJsonEvent(emitter, "final", response);
            emitter.complete();
        } catch (DomainException ex) {
            try {
                sendProblem(emitter, ex);
                emitter.complete();
            } catch (IOException io) {
                log.warn("Dream AI stream problem emit failed", io);
                emitter.completeWithError(io);
            }
        } catch (Exception ex) {
            log.error("Dream AI stream failed", ex);
            try {
                ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Dream AI stream failed");
                pd.setType(URI.create(errorTypeBase + "domain-error"));
                emitter.send(SseEmitter.event().name("problem").data(pd, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException io) {
                emitter.completeWithError(io);
            }
        }
    }

    private void sendProblem(SseEmitter emitter, DomainException ex) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        pd.setType(problemTypeFor(ex.status().value()));
        emitter.send(SseEmitter.event().name("problem").data(pd, MediaType.APPLICATION_JSON));
    }

    private URI problemTypeFor(int status) {
        String suffix = switch (status) {
            case 400 -> "validation-failed";
            case 401 -> "unauthenticated";
            case 403 -> "forbidden";
            case 404 -> "not-found";
            case 409 -> "conflict";
            case 422 -> "moderation-blocked";
            case 429 -> "rate-limited";
            case 502 -> "upstream-error";
            default -> "domain-error";
        };
        return URI.create(errorTypeBase + suffix);
    }

    private void sendJsonEvent(SseEmitter emitter, String eventName, Object body) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(body, MediaType.APPLICATION_JSON));
    }

    /** Deterministic chunks so clients can exercise incremental markdown rendering. */
    static List<String> chunkText(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + maxLen);
            out.add(text.substring(i, end));
            i = end;
        }
        return out;
    }
}
