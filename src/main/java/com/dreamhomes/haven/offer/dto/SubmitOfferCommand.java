package com.dreamhomes.haven.offer.dto;

import java.math.BigDecimal;

public record SubmitOfferCommand(
        Long listingId,
        BigDecimal amount,
        String currency,
        String message
) {
}
