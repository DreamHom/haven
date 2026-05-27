package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.ListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import com.dreamhomes.haven.notification.model.NotificationKind;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bridges Kafka and {@link NotificationApi}: every {@code inspection.requested.v1} event
 * becomes one or two notification rows — always the listing owner, and additionally the
 * active agent (when assigned and distinct from the owner).
 *
 * <p>Manual ack semantics: we acknowledge the offset only after the DB inserts return.
 * If the JVM dies between consume and write, the offset stays at the previous mark and
 * Kafka redelivers — combined with {@code event_id} dedup in {@link NotificationApi},
 * this gives at-least-once-with-effective-once-at-DB.
 *
 * <p>The {@code notifications.event_id} column has a GLOBAL UNIQUE constraint, not
 * per-recipient. So we can't pass the same {@code eventId} twice (the second insert
 * would be deduped). The fan-out trick: the agent row uses a deterministic-but-distinct
 * child event id derived from the original — same idempotency guarantees, no collision
 * with the owner row. See {@link #childEventIdFor(UUID, String)}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InspectionRequestedListener {

    private static final String AGENT_RECIPIENT_SUFFIX = ":agent";

    private final NotificationApi notificationApi;
    private final ListingService listingService;

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

        Long agentUserId = listingService.activeAgentUserId(event.listingId());
        if (agentUserId != null && !agentUserId.equals(event.ownerId())) {
            UUID agentEventId = childEventIdFor(event.eventId(), AGENT_RECIPIENT_SUFFIX);
            log.info("Fanning out inspection.requested.v1 to assigned agentUserId={} childEventId={}",
                    agentUserId, agentEventId);
            notificationApi.recordAsync(agentEventId, NotificationKind.INSPECTION_REQUESTED,
                    agentUserId, event);
        }

        ack.acknowledge();
    }

    /**
     * Derives a deterministic UUID from the original event id + a recipient suffix so
     * multi-recipient fan-out can survive the global UNIQUE on {@code notifications.event_id}.
     * Determinism preserves idempotency: a Kafka redelivery picks the same child id, so the
     * existing dedup short-circuit still kicks in.
     */
    static UUID childEventIdFor(UUID parent, String recipientSuffix) {
        return UUID.nameUUIDFromBytes((parent + recipientSuffix).getBytes(StandardCharsets.UTF_8));
    }
}
