package com.dreamhomes.haven.inspection.events;

import java.time.Instant;

/**
 * Cross-service event: an applicant has just requested an inspection. Carries enough
 * context that consumers (Notification Service for now) don't need to look anything up.
 *
 * <p>Topic: {@code inspection.requested.v1}. Versioned in the topic name so a future
 * payload change can run alongside the old shape during migration.
 */
public record InspectionRequestedEvent(
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
