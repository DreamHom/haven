package com.dreamhomes.haven.domain.listing.dto;

import com.dreamhomes.haven.domain.listing.model.ListingStatus;
import java.math.BigDecimal;

public record UpdateListingRequest(
        String title,
        BigDecimal price,
        ListingStatus status
) {}