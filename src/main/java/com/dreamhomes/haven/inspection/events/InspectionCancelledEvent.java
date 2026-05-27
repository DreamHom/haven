package com.dreamhomes.haven.inspection.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-service event: a PENDING or APPROVED inspection request has been cancelled by
 * one of the parties via the cancel-with-reason flow (Gap C of post-session-tasks
 * Item 7).
 *
 * <p>Topic: {@code inspection.cancelled.v1}. {@code reason} is required (validated at
 * service layer) and carried on the payload so the listener can surface "why" on the
 * notification to the other party.
 *
 * <p>{@code cancelledByUserId} identifies the caller — the listener uses it to figure
 * out which OTHER party (applicant / owner / agent) should be notified.
 */
public record InspectionCancelledEvent(
        UUID eventId,
        Long inspectionRequestId,
        Long slotId,
        Long listingId,
        Long applicantId,
        Long ownerId,
        Long agentUserId,
        Long cancelledByUserId,
        String reason,
        Instant occurredAt
) {
    public static final String TOPIC = "inspection.cancelled.v1";
}
