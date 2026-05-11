package com.dreamhomes.haven.listing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import com.dreamhomes.haven.listing.model.ListingType;

public record CreateListingRequest(
        @NotNull Long propertyId,
        @NotNull ListingType listingType,
        @NotNull @Positive BigDecimal askingPrice,
        @Size(min = 3, max = 3) String currency,
        // Optional fee fields. A solo owner not paying any agent legitimately has all
        // three at 0; @PositiveOrZero accepts that while still rejecting negatives.
        @PositiveOrZero BigDecimal cautionFee,
        @PositiveOrZero BigDecimal serviceCharge,
        @PositiveOrZero BigDecimal agencyFee
) {
    public CreateListingCommand toCommand() {
        return new CreateListingCommand(propertyId, listingType, askingPrice,
                currency, cautionFee, serviceCharge, agencyFee);
    }
}
