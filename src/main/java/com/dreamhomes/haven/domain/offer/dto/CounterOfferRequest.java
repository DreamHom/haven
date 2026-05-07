package com.dreamhomes.haven.domain.offer.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CounterOfferRequest(
        @NotNull BigDecimal counterAmount
) {}

