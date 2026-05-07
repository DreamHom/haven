package com.dreamhomes.haven.listing;

import java.math.BigDecimal;

public record CreateListingCommand(
        Long propertyId,
        ListingType listingType,
        BigDecimal askingPrice,
        String currency,
        BigDecimal cautionFee,
        BigDecimal serviceCharge,
        BigDecimal agencyFee
) {
}
