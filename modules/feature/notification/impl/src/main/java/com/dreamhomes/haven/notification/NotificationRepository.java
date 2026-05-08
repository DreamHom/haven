package com.dreamhomes.haven.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Used by ITs to do exhaustive checks; the API uses the paginated overload below. */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /** Backs {@code GET /api/notifications/mine} — full inbox, newest first. */
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    /**
     * Backs {@code GET /api/notifications/mine?unreadOnly=true}. Uses the composite index
     * from V6 — {@code (recipient_id, read_at, created_at DESC)} — so the unread filter is
     * an index-only seek.
     */
    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    /** Backs the unread-badge counter on the dashboard. */
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /** Idempotency check used by listeners — returns true if this event was already recorded. */
    boolean existsByEventId(UUID eventId);
}
