package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Bridge between Kafka and {@link NotificationService}: every
 * {@code inspection.requested.v1} event becomes a Notification row for the listing owner.
 *
 * <p>Idempotency note: at-least-once delivery means the same event can arrive twice
 * (network blips, consumer rebalancing). Right now we'd persist two rows in that case.
 * Acceptable for MVP; revisit by adding a {@code kafka_message_id} dedup key once it bites.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionRequestedListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = InspectionRequestedEvent.TOPIC, groupId = "haven-notifications")
    public void onInspectionRequested(InspectionRequestedEvent event) {
        log.info("Received inspection.requested.v1 inspectionRequestId={} ownerId={}",
                event.inspectionRequestId(), event.ownerId());
        notificationService.recordInspectionRequested(event);
    }
}
