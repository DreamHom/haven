package com.dreamhomes.haven.listing;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateListingRequest(
        @Positive BigDecimal askingPrice,
        ListingStatus status
) {
    public UpdateListingCommand toCommand() {
        return new UpdateListingCommand(askingPrice, status);
    }
}
