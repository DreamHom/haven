package com.dreamhomes.haven.listing.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import com.dreamhomes.haven.listing.model.ListingStatus;

public record UpdateListingRequest(
        @Positive BigDecimal askingPrice,
        ListingStatus status
) {
    public UpdateListingCommand toCommand() {
        return new UpdateListingCommand(askingPrice, status);
    }
}
