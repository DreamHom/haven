package com.dreamhomes.haven.listing;

import java.math.BigDecimal;
import java.time.Instant;

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
        Instant createdAt,
        Instant updatedAt
) {
    public static ListingResponse from(Listing l) {
        return new ListingResponse(
                l.getId(), l.getPropertyId(), l.getOwnerId(), l.getListingType(),
                l.getAskingPrice(), l.getCurrency(),
                l.getCautionFee(), l.getServiceCharge(), l.getAgencyFee(),
                l.getStatus(), l.getCreatedAt(), l.getUpdatedAt());
    }
}
