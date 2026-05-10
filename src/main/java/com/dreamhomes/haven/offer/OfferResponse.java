package com.dreamhomes.haven.offer;

import java.math.BigDecimal;
import java.time.Instant;

public record OfferResponse(
        Long id,
        Long listingId,
        Long applicantId,
        Long ownerId,
        BigDecimal amount,
        String currency,
        String message,
        OfferStatus status,
        Long parentOfferId,
        Long proposedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
