package com.dreamhomes.haven.domain.listing.dto;

import com.dreamhomes.haven.domain.listing.model.ListingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateListingRequest(
        @NotNull Long propertyId,
        @NotNull ListingType type,
        @NotNull BigDecimal price,
        @NotBlank String title
) {}