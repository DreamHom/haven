package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Bridges Kafka and {@link NotificationService} for offer events. Manual ack — same
 * insert-then-ack discipline as the inspection listener.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OfferSubmittedListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = OfferSubmittedEvent.TOPIC, groupId = "haven-notifications")
    public void onOfferSubmitted(OfferSubmittedEvent event, Acknowledgment ack) {
        log.info("Received offer.submitted.v1 eventId={} offerId={} ownerId={}",
                event.eventId(), event.offerId(), event.ownerId());
        notificationService.recordOfferSubmitted(event);
        ack.acknowledge();
    }
}
