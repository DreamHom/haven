package com.dreamhomes.haven.domain.offer.dto;

import com.dreamhomes.haven.domain.offer.model.OfferStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OfferResponse(
        Long id,
        Long listingId,
        Long applicantId,
        BigDecimal amount,
        OfferStatus status,
        Instant createdAt
) {}

