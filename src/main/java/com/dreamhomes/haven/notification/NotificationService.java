package com.dreamhomes.haven.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.notification.model.NotificationSource;
import com.dreamhomes.haven.notification.exception.NotMyNotificationException;
import com.dreamhomes.haven.notification.exception.NotificationNotFoundException;

/**
 * Implementation of {@link NotificationApi}. Persists notification rows for both
 * synchronous (same-transaction) callers and asynchronous (Kafka-driven) consumers.
 *
 * <p>Async path is idempotent on {@code eventId} — Kafka delivers at-least-once, so
 * the same event can arrive twice; the {@code existsByEventId} check is the
 * service-level half of that guarantee, with the UNIQUE constraint on the column as
 * belt-and-suspenders for the race window between check and insert.
 *
 * <p>Read-side methods (listMine / countUnread / markRead) are NOT on the public API
 * because no other feature needs them — they're called by NotificationController in
 * the same module.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService implements NotificationApi {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final NotificationSseEmitters sseEmitters;

    @Override
    @Transactional
    public void recordSync(NotificationKind kind, Long recipientUserId, Map<String, Object> payload) {
        Notification saved = notificationRepository.save(Notification.builder()
                .recipientId(recipientUserId)
                .kind(kind)
                .source(NotificationSource.SYNC)
                .payload(serialize(payload))
                .build());
        log.info("Recorded sync notificationId={} kind={} recipientId={}",
                saved.getId(), kind, recipientUserId);
        sseEmitters.push(recipientUserId, saved.getId(), kind, payload);
    }

    @Override
    @Transactional
    public void recordAsync(UUID eventId, NotificationKind kind, Long recipientUserId, Object payload) {
        if (notificationRepository.existsByEventId(eventId)) {
            log.info("Skipping duplicate notification eventId={} kind={}", eventId, kind);
            return;
        }
        Notification saved = notificationRepository.save(Notification.builder()
                .eventId(eventId)
                .recipientId(recipientUserId)
                .kind(kind)
                .source(NotificationSource.ASYNC_KAFKA)
                .payload(serialize(payload))
                .build());
        log.info("Recorded async notificationId={} eventId={} kind={} recipientId={}",
                saved.getId(), eventId, kind, recipientUserId);
        // Push the async-sourced row to any subscribed SSE clients too — the recipient
        // doesn't care whether the trigger was an in-process call or a Kafka consumer.
        sseEmitters.push(recipientUserId, saved.getId(), kind,
                payload instanceof Map<?, ?> m ? toStringKeyed(m) : Map.of("payload", payload));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringKeyed(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    /**
     * Read-side: paginated inbox for the authenticated user. {@code unreadOnly=true}
     * picks up the partial composite index from V6 for an index-only seek.
     */
    @Transactional(readOnly = true)
    public Page<Notification> listMine(Long callerId, boolean unreadOnly, Pageable pageable) {
        return listMine(callerId, unreadOnly, null, pageable);
    }

    /**
     * Type-filtered + unread-filter overload. Persona audit (Biodun) flagged that the
     * inbox was unfilterable; at developer scale (12 listings, many event kinds) the
     * inbox becomes a firehose without {@code ?kind=}.
     */
    @Transactional(readOnly = true)
    public Page<Notification> listMine(Long callerId, boolean unreadOnly,
                                       com.dreamhomes.haven.notification.model.NotificationKind kind,
                                       Pageable pageable) {
        if (kind != null && unreadOnly) {
            return notificationRepository
                    .findByRecipientIdAndKindAndReadAtIsNullOrderByCreatedAtDesc(callerId, kind, pageable);
        }
        if (kind != null) {
            return notificationRepository
                    .findByRecipientIdAndKindOrderByCreatedAtDesc(callerId, kind, pageable);
        }
        if (unreadOnly) {
            return notificationRepository
                    .findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(callerId, pageable);
        }
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(callerId, pageable);
    }

    /**
     * Bulk mark-all-read for the caller's inbox. Persona audit (Biodun, Temi) flagged
     * the missing batch action — at scale, tapping one-at-a-time is unusable.
     * Returns the number of notifications actually flipped (already-read are skipped).
     */
    @Transactional
    public int markAllRead(Long callerId) {
        int marked = notificationRepository.markAllReadFor(callerId, Instant.now());
        log.info("Marked {} notifications as read for userId={}", marked, callerId);
        return marked;
    }

    @Transactional(readOnly = true)
    public long countUnread(Long callerId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(callerId);
    }

    /**
     * Stamps {@code readAt} on a single notification. Idempotent: a second mark-read on
     * an already-read notification is a no-op (preserves the original "first read at"
     * timestamp).
     */
    @Transactional
    public Notification markRead(Long callerId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.getRecipientId().equals(callerId)) {
            throw new NotMyNotificationException();
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return notification;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise notification payload", e);
        }
    }
}
