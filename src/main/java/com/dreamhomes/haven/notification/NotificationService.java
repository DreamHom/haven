package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise notification payload", e);
        }
    }
}
