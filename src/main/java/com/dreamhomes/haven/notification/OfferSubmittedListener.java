package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Bridge between Kafka and {@link NotificationService} for offer events. Same
 * idempotency caveat as the inspection listener — at-least-once delivery means a
 * duplicate event would persist a duplicate notification. Acceptable for MVP.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OfferSubmittedListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = OfferSubmittedEvent.TOPIC, groupId = "haven-notifications")
    public void onOfferSubmitted(OfferSubmittedEvent event) {
        log.info("Received offer.submitted.v1 offerId={} ownerId={}",
                event.offerId(), event.ownerId());
        notificationService.recordOfferSubmitted(event);
    }
}
