package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertySummary;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public response shape for a listing. Embeds {@link PropertySummary} for browse cards.
 * Construction lives in {@code feature-listing-impl} (no entity references in this DTO).
 */
public record ListingResponse(
        Long id,
        Long propertyId,
        Long ownerId,
        ListingType listingType,
        BigDecimal askingPrice,
        String currency,
        BigDecimal cautionFee,
        BigDecimal serviceCharge,
        BigDecimal agencyFee,
        ListingStatus status,
        Instant approvedAt,
        Long viewCount,
        Instant createdAt,
        Instant updatedAt,
        PropertySummary property
) {
}
