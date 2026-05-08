package com.dreamhomes.haven.listing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateListingRequest(
        @NotNull Long propertyId,
        @NotNull ListingType listingType,
        @NotNull @Positive BigDecimal askingPrice,
        @Size(min = 3, max = 3) String currency,
        @Positive BigDecimal cautionFee,
        @Positive BigDecimal serviceCharge,
        @Positive BigDecimal agencyFee
) {
    public CreateListingCommand toCommand() {
        return new CreateListingCommand(propertyId, listingType, askingPrice,
                currency, cautionFee, serviceCharge, agencyFee);
    }
}
