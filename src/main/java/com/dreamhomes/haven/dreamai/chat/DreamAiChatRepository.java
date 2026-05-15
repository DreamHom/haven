package com.dreamhomes.haven.dreamai.chat;

import com.dreamhomes.haven.dreamai.chat.model.DreamAiChat;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DreamAiChatRepository extends JpaRepository<DreamAiChat, Long> {

    Page<DreamAiChat> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Optional<DreamAiChat> findByIdAndUserId(Long id, Long userId);
}
