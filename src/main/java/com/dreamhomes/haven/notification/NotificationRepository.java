package com.dreamhomes.haven.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /** Idempotency check used by listeners — returns true if this event was already recorded. */
    boolean existsByEventId(UUID eventId);
}
