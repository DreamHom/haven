package com.dreamhomes.haven.inspection.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-service event: an applicant has just requested an inspection. {@code eventId}
 * is generated at outbox-write time and travels with the payload so consumers can
 * dedup duplicate deliveries (Kafka is at-least-once by default).
 *
 * <p>Topic: {@code inspection.requested.v1}. Versioned in the topic name so a future
 * payload change can run alongside the old shape during migration.
 */
public record InspectionRequestedEvent(
        UUID eventId,
        Long inspectionRequestId,
        Long slotId,
        Long listingId,
        Long ownerId,
        Long applicantId,
        Instant startsAt,
        Instant endsAt,
        Instant requestedAt
) {
    public static final String TOPIC = "inspection.requested.v1";
}
