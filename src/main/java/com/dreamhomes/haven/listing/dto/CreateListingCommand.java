package com.dreamhomes.haven.listing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.dreamhomes.haven.listing.model.ListingType;

public record CreateListingCommand(
        Long propertyId,
        ListingType listingType,
        BigDecimal askingPrice,
        String currency,
        BigDecimal cautionFee,
        BigDecimal serviceCharge,
        BigDecimal agencyFee,
        String title,
        String description,
        String headline,
        LocalDate handoverDate,
        String virtualTourUrl,
        boolean priceNegotiable,
        String floorPlanUrl,
        String petsAllowed,
        String utilitiesNote) {
}
