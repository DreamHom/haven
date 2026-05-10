package com.dreamhomes.haven.offer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CounterOfferRequest(
        @NotNull @Positive BigDecimal amount,
        @Size(max = 5000) String message
) {
}
