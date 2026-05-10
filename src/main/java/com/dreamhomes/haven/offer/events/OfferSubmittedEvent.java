package com.dreamhomes.haven.offer.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.dreamhomes.haven.notification.model.Notification;

/**
 * Cross-service event: an applicant has submitted a formal offer. Highest-stakes event
 * on the platform — the consumer (Notification Service) MUST receive it reliably.
 * {@code eventId} is generated at outbox-write time so duplicate deliveries dedup.
 *
 * <p>Topic: {@code offer.submitted.v1}.
 */
public record OfferSubmittedEvent(
        UUID eventId,
        Long offerId,
        Long listingId,
        Long ownerId,
        Long applicantId,
        BigDecimal amount,
        String currency,
        Instant submittedAt
) {
    public static final String TOPIC = "offer.submitted.v1";
}
