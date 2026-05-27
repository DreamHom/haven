package com.dreamhomes.haven.inspection.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-service event: the listing owner has approved or declined a pending inspection
 * request. {@code eventId} is generated at outbox-write time and travels with the payload
 * so consumers can dedup duplicate Kafka deliveries.
 *
 * <p>Topic: {@code inspection.decided.v1}. Versioned in the topic name so a future
 * payload change can run alongside the old shape during migration.
 *
 * <p>{@code decision} is one of {@code APPROVED} or {@code DECLINED}. {@code reason} is
 * optional — null when the owner approved or declined without supplying a justification.
 * The downstream listener surfaces the reason to the applicant on the in-tray notification.
 */
public record InspectionDecidedEvent(
        UUID eventId,
        Long inspectionRequestId,
        Long slotId,
        Long listingId,
        Long applicantId,
        Decision decision,
        String reason,
        Instant occurredAt
) {
    public static final String TOPIC = "inspection.decided.v1";

    public enum Decision {
        APPROVED,
        DECLINED
    }
}
