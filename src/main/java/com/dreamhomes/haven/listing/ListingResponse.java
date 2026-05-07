package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertySummary;

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
        Instant approvedAt,
        Long viewCount,
        Instant createdAt,
        Instant updatedAt,
        PropertySummary property
) {
    public static ListingResponse from(Listing l, Property p) {
        return new ListingResponse(
                l.getId(), l.getPropertyId(), l.getOwnerId(), l.getListingType(),
                l.getAskingPrice(), l.getCurrency(),
                l.getCautionFee(), l.getServiceCharge(), l.getAgencyFee(),
                l.getStatus(), l.getApprovedAt(),
                l.getViewCount(),
                l.getCreatedAt(), l.getUpdatedAt(),
                p == null ? null : PropertySummary.from(p));
    }

    public static ListingResponse from(ListingWithProperty lwp) {
        return from(lwp.listing(), lwp.property());
    }

    /** For write paths (create/update) where we don't bundle a property in the response. */
    public static ListingResponse fromWithoutProperty(Listing l) {
        return from(l, null);
    }
}
