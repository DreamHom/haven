package com.dreamhomes.haven.listing.dto;

import java.math.BigDecimal;
import com.dreamhomes.haven.listing.model.ListingStatus;

/**
 * Mutable fields on an existing listing. Both can be null (caller may want to change
 * just one). The service applies whichever are present.
 */
public record UpdateListingCommand(BigDecimal askingPrice, ListingStatus status) {
}
