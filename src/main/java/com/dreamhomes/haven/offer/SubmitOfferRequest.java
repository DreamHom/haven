package com.dreamhomes.haven.offer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SubmitOfferRequest(
        @NotNull Long listingId,
        @NotNull @Positive BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 5000) String message
) {
    public SubmitOfferCommand toCommand() {
        return new SubmitOfferCommand(listingId, amount, currency, message);
    }
}
