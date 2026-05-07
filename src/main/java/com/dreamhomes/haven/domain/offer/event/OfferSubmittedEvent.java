package com.dreamhomes.haven.domain.offer.event;

import java.math.BigDecimal;
import java.time.Instant;

public record OfferSubmittedEvent(
        Long offerId,
        Long listingId,
        Long applicantId,
        BigDecimal amount,
        Instant submittedAt
) {}

