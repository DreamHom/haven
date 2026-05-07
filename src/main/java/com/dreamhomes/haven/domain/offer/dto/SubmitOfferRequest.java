package com.dreamhomes.haven.domain.offer.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SubmitOfferRequest(
        @NotNull Long listingId,
        @NotNull Long applicantId,
        @NotNull BigDecimal amount
) {}

