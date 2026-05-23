package com.dreamhomes.haven.dreamai.chat;

import com.dreamhomes.haven.dreamai.chat.model.DreamAiChat;
import com.dreamhomes.haven.dreamai.chat.model.DreamAiChatMessage;
import com.dreamhomes.haven.dreamai.chat.model.DreamAiChatMessageRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DreamAiChatMessageRepository extends JpaRepository<DreamAiChatMessage, Long> {

    List<DreamAiChatMessage> findByChatOrderByCreatedAtAsc(DreamAiChat chat);

    boolean existsByChat_IdAndClientMessageId(Long chatId, String clientMessageId);

    Optional<DreamAiChatMessage> findFirstByChat_IdAndClientMessageId(Long chatId, String clientMessageId);

    @Query("select m from DreamAiChatMessage m where m.chat.id = :cid and m.role = :assistant and m.id > :afterId order by m.id asc")
    List<DreamAiChatMessage> findAssistantsAfter(
            @Param("cid") Long chatId,
            @Param("assistant") DreamAiChatMessageRole assistant,
            @Param("afterId") Long afterUserMessageId,
            Pageable pageable);

    @Query("select m from DreamAiChatMessage m join fetch m.chat c where c.userId = :uid and m.clientMessageId = :cid order by m.createdAt desc")
    List<DreamAiChatMessage> findUserMessagesForUserAndClient(
            @Param("uid") Long userId, @Param("cid") String clientMessageId, Pageable pageable);

    /**
     * Most recent message of a given role on a chat. Backs the conversation-aware compare
     * path — the orchestrator needs the prior assistant turn's listing ids and the user
     * prompt that produced them when responding to a "which is best?" follow-up.
     */
    @Query("select m from DreamAiChatMessage m where m.chat.id = :cid and m.role = :role order by m.createdAt desc")
    List<DreamAiChatMessage> findLatestByChatAndRole(
            @Param("cid") Long chatId, @Param("role") DreamAiChatMessageRole role, Pageable pageable);
}
