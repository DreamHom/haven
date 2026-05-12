package com.dreamhomes.haven.offer.dto;

import com.dreamhomes.haven.offer.model.OfferIntent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SubmitOfferRequest(
        @NotNull Long listingId,
        @NotNull @Positive BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 5000) String message,
        /**
         * Applicant's intent — optional. Persona audit (Ngozi) flagged this as the
         * highest-priority schema gap for rent-to-buy applicants.
         */
        OfferIntent intent
) {
}
