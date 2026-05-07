package com.dreamhomes.haven.offer.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cross-service event: an applicant has submitted a formal offer. Highest-stakes event
 * on the platform — the consumer (Notification Service) MUST receive it for the deal to
 * survive.
 *
 * <p>Topic: {@code offer.submitted.v1}.
 */
public record OfferSubmittedEvent(
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
