package com.dreamhomes.haven.listing.dto;

import com.dreamhomes.haven.property.dto.PropertySummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;

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
        String title,
        String description,
        String headline,
        LocalDate handoverDate,
        ListingStatus status,
        Instant approvedAt,
        Long viewCount,
        Instant createdAt,
        Instant updatedAt,
        PropertySummary property,
        Long assignedAgentId,
        Long pendingReportCount
) {
}
