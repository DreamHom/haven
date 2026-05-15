package com.dreamhomes.haven.offer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.dreamhomes.haven.offer.model.OfferIntent;
import com.dreamhomes.haven.offer.model.OfferStatus;
public record OfferResponse(
        Long id,
        Long listingId,
        Long applicantId,
        Long ownerId,
        BigDecimal amount,
        String currency,
        String message,
        OfferIntent intent,
        OfferStatus status,
        Long parentOfferId,
        Long proposedByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
