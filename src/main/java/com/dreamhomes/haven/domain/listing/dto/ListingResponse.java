package com.dreamhomes.haven.domain.listing.dto;

import com.dreamhomes.haven.domain.listing.model.ListingStatus;
import com.dreamhomes.haven.domain.listing.model.ListingType;
import java.math.BigDecimal;

public record ListingResponse(
        Long id,
        Long propertyId,
        ListingType type,
        ListingStatus status,
        BigDecimal price,
        String title
) {}

