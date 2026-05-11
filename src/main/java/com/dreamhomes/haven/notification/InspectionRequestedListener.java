package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;

/**
 * Bridges Kafka and {@link NotificationApi}: every {@code inspection.requested.v1} event
 * becomes a Notification row for the listing owner.
 *
 * <p>Manual ack semantics: we acknowledge the offset only after the DB insert returns.
 * If the JVM dies between consume and write, the offset stays at the previous mark and
 * Kafka redelivers — combined with {@code event_id} dedup in {@link NotificationApi},
 * this gives at-least-once-with-effective-once-at-DB.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionRequestedListener {

    private final NotificationApi notificationApi;

    @KafkaListener(
            topics = InspectionRequestedEvent.TOPIC,
            groupId = "haven-notifications",
            // Match consumer-side parallelism to the topic's partition count so every
            // partition can be drained in parallel. Default of 1 left N-1 partitions
            // queueing behind a single consumer thread.
            concurrency = "${haven.kafka.topic-partitions:3}")
    public void onInspectionRequested(InspectionRequestedEvent event, Acknowledgment ack) {
        log.info("Received inspection.requested.v1 eventId={} inspectionRequestId={} ownerId={}",
                event.eventId(), event.inspectionRequestId(), event.ownerId());
        notificationApi.recordAsync(event.eventId(), NotificationKind.INSPECTION_REQUESTED,
                event.ownerId(), event);
        ack.acknowledge();
    }
}
