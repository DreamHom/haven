package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import com.dreamhomes.haven.notification.model.NotificationKind;

import java.util.UUID;

/**
 * Bridges Kafka and {@link NotificationApi} for cancellations from the
 * cancel-with-reason flow (Gap C of post-session-tasks Item 7).
 *
 * <p>Notifies every party EXCEPT the one who pressed cancel — the canceller already
 * knows what happened. Recipient ordering matches the way other multi-recipient
 * fan-outs work: the parent {@code event_id} goes to the "primary other party"
 * (applicant if owner/agent cancelled; owner if applicant cancelled) and a derived
 * child id covers any remaining recipient so the global UNIQUE on
 * {@code notifications.event_id} doesn't block the second insert.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionCancelledListener {

    private final NotificationApi notificationApi;

    @KafkaListener(
            topics = InspectionCancelledEvent.TOPIC,
            groupId = "haven-notifications",
            concurrency = "${haven.kafka.topic-partitions:3}")
    public void onInspectionCancelled(InspectionCancelledEvent event, Acknowledgment ack) {
        log.info("Received inspection.cancelled.v1 eventId={} inspectionRequestId={} cancelledByUserId={}",
                event.eventId(), event.inspectionRequestId(), event.cancelledByUserId());

        Long applicant = event.applicantId();
        Long owner = event.ownerId();
        Long agent = event.agentUserId();
        Long canceller = event.cancelledByUserId();

        boolean cancellerIsApplicant = canceller != null && canceller.equals(applicant);
        boolean cancellerIsOwner = canceller != null && canceller.equals(owner);

        // Primary recipient: the party who is most likely directly impacted.
        //   Applicant cancelled → owner is primary; agent (if present) is secondary.
        //   Owner / agent cancelled → applicant is primary; the other team-member is secondary.
        Long primary;
        Long secondary;
        String secondarySuffix;
        if (cancellerIsApplicant) {
            primary = owner;
            secondary = (agent != null && !agent.equals(canceller) && !agent.equals(owner)) ? agent : null;
            secondarySuffix = ":agent";
        } else if (cancellerIsOwner) {
            primary = applicant;
            secondary = (agent != null && !agent.equals(canceller)) ? agent : null;
            secondarySuffix = ":agent";
        } else {
            // Agent (or any other authorised caller) cancelled.
            primary = applicant;
            secondary = (owner != null && !owner.equals(canceller)) ? owner : null;
            secondarySuffix = ":owner";
        }

        if (primary != null && !primary.equals(canceller)) {
            notificationApi.recordAsync(event.eventId(), NotificationKind.INSPECTION_CANCELLED,
                    primary, event);
        }
        if (secondary != null && !secondary.equals(primary)) {
            UUID secondaryEventId = InspectionRequestedListener.childEventIdFor(
                    event.eventId(), secondarySuffix);
            log.info("Fanning out inspection.cancelled.v1 to secondary recipientUserId={} childEventId={}",
                    secondary, secondaryEventId);
            notificationApi.recordAsync(secondaryEventId,
                    NotificationKind.INSPECTION_CANCELLED, secondary, event);
        }

        ack.acknowledge();
    }
}
