package com.dreamhomes.haven.dreamai.chat;

import com.dreamhomes.haven.dreamai.DreamAiTurnOrchestrator;
import com.dreamhomes.haven.dreamai.chat.dto.DreamAiChatDetailResponse;
import com.dreamhomes.haven.dreamai.chat.dto.DreamAiChatMessageResponse;
import com.dreamhomes.haven.dreamai.chat.dto.DreamAiChatSummaryResponse;
import com.dreamhomes.haven.dreamai.chat.exception.DreamAiChatNotFoundException;
import com.dreamhomes.haven.dreamai.chat.model.DreamAiChat;
import com.dreamhomes.haven.dreamai.chat.model.DreamAiChatMessage;
import com.dreamhomes.haven.dreamai.chat.model.DreamAiChatMessageRole;
import com.dreamhomes.haven.dreamai.chat.payload.DreamAiMessageDocumentV1;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnRequest;
import com.dreamhomes.haven.dreamai.dto.DreamAiRunTurnResponse;
import com.dreamhomes.haven.dreamai.moderation.DreamAiModerationService;
import com.dreamhomes.haven.dreamai.turn.AssistantTurnV1;
import com.dreamhomes.haven.dreamai.turn.TurnBlock;
import com.dreamhomes.haven.dreamai.turn.TurnMeta;
import com.dreamhomes.haven.listing.ListingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DreamAiChatService {

    private static final int PREVIEW_MAX = 200;

    private final DreamAiChatRepository dreamAiChatRepository;
    private final DreamAiChatMessageRepository dreamAiChatMessageRepository;
    private final DreamAiTurnOrchestrator dreamAiTurnOrchestrator;
    private final DreamAiModerationService dreamAiModerationService;
    private final ListingService listingService;
    private final ObjectMapper objectMapper;

    /**
     * One-shot Dream AI turn. {@code userId} is nullable — when null the call is
     * anonymous (Vista's public /dream-ai page hitting us SSR-side without a JWT):
     * the response is computed normally but the chat + message rows are NOT
     * persisted, idempotent replay is skipped, and the returned {@code chatId}
     * is null. Logged-in callers get the full persistence path.
     */
    @Transactional
    public DreamAiRunTurnResponse runTurn(Long userId, DreamAiRunTurnRequest request) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("dreamAiUserId", userId == null ? "anonymous" : String.valueOf(userId));
        try {
            String effective = resolveEffectivePrompt(request);
            if (effective.isBlank()) {
                throw new DreamAiBadPromptException();
            }
            dreamAiModerationService.assertAllowed(effective);

            // Anonymous fast path: no DB writes, no replay lookup. Just orchestrate
            // and return. Anonymous callers can't reference an existing chatId
            // either — silently treat that as a fresh one-shot.
            if (userId == null) {
                AssistantTurnV1 anonymousTurn = dreamAiTurnOrchestrator.buildTurn(effective, traceId);
                anonymousTurn = stampTraceOnTurn(anonymousTurn, traceId);
                log.info("Dream AI turn (anonymous) kind={}", anonymousTurn.kind());
                return toRunResponse(null, traceId, anonymousTurn);
            }

            Optional<DreamAiRunTurnResponse> replay =
                    tryReplayIdempotent(userId, request.chatId(), request.clientMessageId());
            if (replay.isPresent()) {
                log.info("Dream AI idempotent replay chatId={} clientMessageId={}", replay.get().chatId(), request.clientMessageId());
                return replay.get();
            }

            DreamAiChat chat = resolveOrCreateChat(userId, request.chatId(), effective);

            // Conversation context for the orchestrator. When the prior assistant turn
            // surfaced listings AND this prompt looks like a comparison question, the
            // orchestrator routes through the AI-backed compare path instead of running
            // a fresh search.
            PriorTurnContext prior = readPriorTurnContext(chat);

            DreamAiMessageDocumentV1 userDoc = DreamAiMessageDocumentV1.user(effective, blankToNull(request.clientMessageId()));
            dreamAiChatMessageRepository.save(DreamAiChatMessage.builder()
                    .chat(chat)
                    .role(DreamAiChatMessageRole.USER)
                    .clientMessageId(blankToNull(request.clientMessageId()))
                    .content(toJsonNode(userDoc))
                    .build());

            AssistantTurnV1 turn = dreamAiTurnOrchestrator.buildTurn(
                    effective, traceId, prior.listingIds(), prior.userPrompt());
            turn = stampTraceOnTurn(turn, traceId);

            DreamAiMessageDocumentV1 assistantDoc = DreamAiMessageDocumentV1.assistant(turn);
            dreamAiChatMessageRepository.save(DreamAiChatMessage.builder()
                    .chat(chat)
                    .role(DreamAiChatMessageRole.ASSISTANT)
                    .clientMessageId(null)
                    .content(toJsonNode(assistantDoc))
                    .build());

            chat.setUpdatedAt(Instant.now());
            dreamAiChatRepository.save(chat);

            log.info("Dream AI turn persisted chatId={} kind={}", chat.getId(), turn.kind());
            return toRunResponse(chat.getId(), traceId, turn);
        } finally {
            MDC.remove("traceId");
            MDC.remove("dreamAiUserId");
        }
    }

    @Transactional(readOnly = true)
    public Page<DreamAiChatSummaryResponse> listMine(long userId, Pageable pageable) {
        return dreamAiChatRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable)
                .map(c -> new DreamAiChatSummaryResponse(c.getId(), c.getPreview(), c.getCreatedAt(), c.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public DreamAiChatDetailResponse getChat(long userId, long chatId) {
        DreamAiChat chat = dreamAiChatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(DreamAiChatNotFoundException::new);
        List<DreamAiChatMessage> rows = dreamAiChatMessageRepository.findByChatOrderByCreatedAtAsc(chat);
        List<DreamAiChatMessageResponse> messages = new ArrayList<>();
        for (DreamAiChatMessage row : rows) {
            try {
                messages.add(mapMessage(row));
            } catch (JsonProcessingException e) {
                log.warn("Dream AI message id={} could not be decoded", row.getId(), e);
            }
        }
        DreamAiChatSummaryResponse summary = new DreamAiChatSummaryResponse(
                chat.getId(), chat.getPreview(), chat.getCreatedAt(), chat.getUpdatedAt());
        return new DreamAiChatDetailResponse(summary, messages);
    }

    private DreamAiChatMessageResponse mapMessage(DreamAiChatMessage m) throws JsonProcessingException {
        DreamAiMessageDocumentV1 doc = objectMapper.treeToValue(m.getContent(), DreamAiMessageDocumentV1.class);
        if (m.getRole() == DreamAiChatMessageRole.USER) {
            return new DreamAiChatMessageResponse(
                    m.getId(), m.getRole(), m.getClientMessageId(), doc.schemaVersion(), doc.userText(), null, m.getCreatedAt());
        }
        AssistantTurnV1 raw = doc.turn();
        AssistantTurnV1 hydrated = raw == null ? null : rehydrateListingBlocks(raw);
        return new DreamAiChatMessageResponse(
                m.getId(), m.getRole(), m.getClientMessageId(), doc.schemaVersion(), null, hydrated, m.getCreatedAt());
    }

    private AssistantTurnV1 rehydrateListingBlocks(AssistantTurnV1 turn) {
        boolean stale = false;
        List<TurnBlock> nextBlocks = new ArrayList<>();
        for (TurnBlock b : turn.blocks()) {
            if (!"listings".equals(b.type())) {
                nextBlocks.add(b);
                continue;
            }
            List<Long> live = listingService.liveListingIdsAmong(b.listingIds());
            if (live.size() != b.listingIds().size()) {
                stale = true;
            }
            nextBlocks.add(TurnBlock.listings(live));
        }
        TurnMeta meta = turn.meta();
        TurnMeta nextMeta = new TurnMeta(
                meta.inventoryEmpty(),
                meta.queryTooStrict(),
                meta.degraded(),
                meta.provider(),
                meta.traceId(),
                meta.moderationBlocked(),
                meta.retryable(),
                stale ? Boolean.TRUE : meta.staleIdsFiltered());
        return new AssistantTurnV1(turn.kind(), turn.markdown(), nextBlocks, nextMeta);
    }

    private Optional<DreamAiRunTurnResponse> tryReplayIdempotent(Long userId, Long chatId, String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return Optional.empty();
        }
        if (chatId != null) {
            dreamAiChatRepository.findByIdAndUserId(chatId, userId).orElseThrow(DreamAiChatNotFoundException::new);
            if (!dreamAiChatMessageRepository.existsByChat_IdAndClientMessageId(chatId, clientMessageId)) {
                return Optional.empty();
            }
            DreamAiChatMessage user = dreamAiChatMessageRepository
                    .findFirstByChat_IdAndClientMessageId(chatId, clientMessageId)
                    .orElseThrow();
            return replayFromUserRow(user);
        }
        List<DreamAiChatMessage> rows = dreamAiChatMessageRepository.findUserMessagesForUserAndClient(
                userId, clientMessageId, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return replayFromUserRow(rows.getFirst());
    }

    private Optional<DreamAiRunTurnResponse> replayFromUserRow(DreamAiChatMessage userRow) {
        List<DreamAiChatMessage> assistants = dreamAiChatMessageRepository.findAssistantsAfter(
                userRow.getChat().getId(),
                DreamAiChatMessageRole.ASSISTANT,
                userRow.getId(),
                PageRequest.of(0, 1));
        if (assistants.isEmpty()) {
            return Optional.empty();
        }
        DreamAiChatMessage asst = assistants.getFirst();
        try {
            DreamAiMessageDocumentV1 doc = objectMapper.treeToValue(asst.getContent(), DreamAiMessageDocumentV1.class);
            AssistantTurnV1 turn = doc.turn();
            if (turn == null) {
                return Optional.empty();
            }
            return Optional.of(toRunResponse(userRow.getChat().getId(), traceFromTurn(turn), turn));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static String traceFromTurn(AssistantTurnV1 turn) {
        if (turn.meta() != null && turn.meta().traceId() != null && !turn.meta().traceId().isBlank()) {
            return turn.meta().traceId();
        }
        return "replay";
    }

    private DreamAiRunTurnResponse toRunResponse(Long chatId, String traceId, AssistantTurnV1 turn) {
        return new DreamAiRunTurnResponse(chatId, traceId, turn, listingIdsFromTurn(turn));
    }

    public static List<Long> listingIdsFromTurn(AssistantTurnV1 turn) {
        if (turn == null || turn.blocks() == null) {
            return List.of();
        }
        return turn.blocks().stream()
                .filter(b -> "listings".equals(b.type()))
                .flatMap(b -> b.listingIds().stream())
                .toList();
    }

    private static AssistantTurnV1 stampTraceOnTurn(AssistantTurnV1 turn, String traceId) {
        TurnMeta m = turn.meta();
        TurnMeta next = new TurnMeta(
                m.inventoryEmpty(),
                m.queryTooStrict(),
                m.degraded(),
                m.provider(),
                traceId,
                m.moderationBlocked(),
                m.retryable(),
                m.staleIdsFiltered());
        return new AssistantTurnV1(turn.kind(), turn.markdown(), turn.blocks(), next);
    }

    private JsonNode toJsonNode(Object o) {
        return objectMapper.valueToTree(o);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static String resolveEffectivePrompt(DreamAiRunTurnRequest req) {
        if (req.userChoice() != null
                && req.userChoice().sendText() != null
                && !req.userChoice().sendText().isBlank()) {
            return req.userChoice().sendText().trim();
        }
        return req.prompt() == null ? "" : req.prompt().trim();
    }

    /**
     * Pulls "what was the last assistant turn about?" + "what user prompt produced it?" from
     * persistence so the orchestrator can route comparison-style follow-ups
     * ("which is best?", "compare these for a single mum") through the AI compare path
     * with the prior listing ids and the original constraint context.
     *
     * <p>Empty record when this is the first turn on the chat — the orchestrator falls
     * back to the URL-trigger / clarify / rank paths in that case.</p>
     */
    private PriorTurnContext readPriorTurnContext(DreamAiChat chat) {
        if (chat == null || chat.getId() == null) {
            return PriorTurnContext.empty();
        }
        var assistantPage = dreamAiChatMessageRepository.findLatestByChatAndRole(
                chat.getId(), DreamAiChatMessageRole.ASSISTANT,
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (assistantPage.isEmpty()) {
            return PriorTurnContext.empty();
        }
        DreamAiChatMessage assistantRow = assistantPage.get(0);
        List<Long> ids;
        try {
            DreamAiMessageDocumentV1 doc = objectMapper.treeToValue(
                    assistantRow.getContent(), DreamAiMessageDocumentV1.class);
            ids = listingIdsFromTurn(doc.turn());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.debug("Could not decode prior assistant message {} for compare context", assistantRow.getId());
            ids = List.of();
        }
        if (ids.isEmpty()) {
            return PriorTurnContext.empty();
        }
        // Pair with the most recent USER prompt (the one that produced this assistant turn,
        // or the freshest user prompt if ordering is ambiguous).
        var userPage = dreamAiChatMessageRepository.findLatestByChatAndRole(
                chat.getId(), DreamAiChatMessageRole.USER,
                org.springframework.data.domain.PageRequest.of(0, 1));
        String priorPrompt = null;
        if (!userPage.isEmpty()) {
            try {
                DreamAiMessageDocumentV1 userDoc = objectMapper.treeToValue(
                        userPage.get(0).getContent(), DreamAiMessageDocumentV1.class);
                priorPrompt = userDoc.userText();
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                log.debug("Could not decode prior user message {} for compare context", userPage.get(0).getId());
            }
        }
        return new PriorTurnContext(ids, priorPrompt);
    }

    /**
     * Snapshot of the prior assistant turn's listing ids + the user prompt that produced
     * them, used by the orchestrator's conversation-aware compare path.
     */
    private record PriorTurnContext(List<Long> listingIds, String userPrompt) {
        static PriorTurnContext empty() {
            return new PriorTurnContext(List.of(), null);
        }
    }

    private DreamAiChat resolveOrCreateChat(long userId, Long existingChatId, String promptTrimmed) {
        if (existingChatId == null) {
            String preview = promptTrimmed.length() <= PREVIEW_MAX
                    ? promptTrimmed
                    : promptTrimmed.substring(0, PREVIEW_MAX);
            return dreamAiChatRepository.save(DreamAiChat.builder()
                    .userId(userId)
                    .preview(preview)
                    .build());
        }
        return dreamAiChatRepository.findByIdAndUserId(existingChatId, userId)
                .orElseThrow(DreamAiChatNotFoundException::new);
    }
}
