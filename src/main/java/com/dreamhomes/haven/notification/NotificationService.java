package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists notification rows triggered by Kafka events. Idempotent on {@code eventId} —
 * Kafka delivers at-least-once, so the same event can arrive twice; the
 * {@code existsByEventId} check is the service-level half of that guarantee, with the
 * UNIQUE constraint on the column as belt-and-suspenders for the race window between
 * check and insert.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<Notification> recordInspectionRequested(InspectionRequestedEvent event) {
        return record(event.eventId(), event.ownerId(), NotificationKind.INSPECTION_REQUESTED, event);
    }

    @Transactional
    public Optional<Notification> recordOfferSubmitted(OfferSubmittedEvent event) {
        return record(event.eventId(), event.ownerId(), NotificationKind.OFFER_SUBMITTED, event);
    }

    private Optional<Notification> record(UUID eventId, Long recipientId, NotificationKind kind, Object payload) {
        if (notificationRepository.existsByEventId(eventId)) {
            log.info("Skipping duplicate notification eventId={} kind={}", eventId, kind);
            return Optional.empty();
        }
        Notification saved = notificationRepository.save(Notification.builder()
                .eventId(eventId)
                .recipientId(recipientId)
                .kind(kind)
                .source(NotificationSource.ASYNC_KAFKA)
                .payload(serialize(payload))
                .createdAt(Instant.now())
                .build());
        log.info("Recorded notificationId={} eventId={} kind={} recipientId={}",
                saved.getId(), eventId, saved.getKind(), saved.getRecipientId());
        return Optional.of(saved);
    }

    /**
     * Read-side: paginated inbox for the authenticated user. {@code unreadOnly=true}
     * picks up the partial composite index from V6 for an index-only seek.
     */
    @Transactional(readOnly = true)
    public Page<Notification> listMine(Long callerId, boolean unreadOnly, Pageable pageable) {
        if (unreadOnly) {
            return notificationRepository
                    .findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(callerId, pageable);
        }
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(callerId, pageable);
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
