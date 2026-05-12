package com.dreamhomes.haven.offer.dto;

import com.dreamhomes.haven.offer.model.OfferIntent;

import java.math.BigDecimal;

public record SubmitOfferCommand(
        Long listingId,
        BigDecimal amount,
        String currency,
        String message,
        OfferIntent intent
) {
}
