package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionDecidedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import com.dreamhomes.haven.notification.model.NotificationKind;

/**
 * Bridges Kafka and {@link NotificationApi}: every {@code inspection.decided.v1} event
 * becomes a Notification row for the applicant — APPROVED or DECLINED depending on the
 * decision recorded by the owner. Closes Gap B of post-session-tasks Item 7 — before
 * this listener the applicant had to refresh the page to learn the outcome.
 *
 * <p>Manual ack semantics match {@link InspectionRequestedListener}: insert first,
 * acknowledge after. Kafka redelivery + the {@code event_id} UNIQUE on
 * {@code notifications} gives effectively-once-at-DB.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionDecidedListener {

    private final NotificationApi notificationApi;

    @KafkaListener(
            topics = InspectionDecidedEvent.TOPIC,
            groupId = "haven-notifications",
            // Match consumer-side parallelism to topic partitions, like the sibling listener.
            concurrency = "${haven.kafka.topic-partitions:3}")
    public void onInspectionDecided(InspectionDecidedEvent event, Acknowledgment ack) {
        log.info("Received inspection.decided.v1 eventId={} inspectionRequestId={} decision={}",
                event.eventId(), event.inspectionRequestId(), event.decision());
        NotificationKind kind = switch (event.decision()) {
            case APPROVED -> NotificationKind.INSPECTION_APPROVED;
            case DECLINED -> NotificationKind.INSPECTION_DECLINED;
        };
        notificationApi.recordAsync(event.eventId(), kind, event.applicantId(), event);
        ack.acknowledge();
    }
}
