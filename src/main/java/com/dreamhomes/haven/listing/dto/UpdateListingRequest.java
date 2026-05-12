package com.dreamhomes.haven.listing.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.dreamhomes.haven.listing.model.ListingStatus;

public record UpdateListingRequest(
        @Positive BigDecimal askingPrice,
        ListingStatus status,
        @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @Size(max = 255) String headline,
        LocalDate handoverDate
) {
    public UpdateListingCommand toCommand() {
        return new UpdateListingCommand(askingPrice, status, title, description, headline, handoverDate);
    }
}
