package com.dreamhomes.haven.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Used by ITs to do exhaustive checks; the API uses the paginated overload below. */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /** Backs {@code GET /api/notifications/mine} — full inbox, newest first. */
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    /** Backs {@code GET /api/notifications/mine?kind=...} — type-filtered inbox. */
    Page<Notification> findByRecipientIdAndKindOrderByCreatedAtDesc(
            Long recipientId, NotificationKind kind, Pageable pageable);

    /**
     * Backs {@code GET /api/notifications/mine?unreadOnly=true}. Uses the composite index
     * from V6 — {@code (recipient_id, read_at, created_at DESC)} — so the unread filter is
     * an index-only seek.
     */
    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    /** Combined unread + kind filter. */
    Page<Notification> findByRecipientIdAndKindAndReadAtIsNullOrderByCreatedAtDesc(
            Long recipientId, NotificationKind kind, Pageable pageable);

    /** Backs the unread-badge counter on the dashboard. */
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /** Idempotency check used by listeners — returns true if this event was already recorded. */
    boolean existsByEventId(UUID eventId);

    /**
     * Bulk-stamp every unread notification for a recipient as read at the given instant.
     * Backs {@code POST /api/notifications/mark-all-read}. Returns the number of rows
     * actually updated (excludes already-read).
     */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt " +
           "WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllReadFor(@Param("recipientId") Long recipientId, @Param("readAt") Instant readAt);
}
