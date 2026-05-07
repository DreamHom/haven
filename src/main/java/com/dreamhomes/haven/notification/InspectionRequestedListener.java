package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Bridges Kafka and {@link NotificationService}: every {@code inspection.requested.v1}
 * event becomes a Notification row for the listing owner.
 *
 * <p>Manual ack semantics: we acknowledge the offset only after the DB insert returns.
 * If the JVM dies between consume and write, the offset stays at the previous mark and
 * Kafka redelivers — combined with {@code event_id} dedup in
 * {@link NotificationService}, this gives at-least-once-with-effective-once-at-DB.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionRequestedListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = InspectionRequestedEvent.TOPIC, groupId = "haven-notifications")
    public void onInspectionRequested(InspectionRequestedEvent event, Acknowledgment ack) {
        log.info("Received inspection.requested.v1 eventId={} inspectionRequestId={} ownerId={}",
                event.eventId(), event.inspectionRequestId(), event.ownerId());
        notificationService.recordInspectionRequested(event);
        ack.acknowledge();
    }
}
