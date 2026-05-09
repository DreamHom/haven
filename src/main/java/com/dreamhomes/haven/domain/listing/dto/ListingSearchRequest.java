package com.dreamhomes.haven.domain.listing.dto;

import com.dreamhomes.haven.domain.listing.model.ListingStatus;
import com.dreamhomes.haven.domain.listing.model.ListingType;
import java.math.BigDecimal;

public record ListingSearchRequest(
        ListingType type,
        ListingStatus status,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String query
) {}