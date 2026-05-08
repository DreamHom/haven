package com.dreamhomes.haven.listing;

import java.math.BigDecimal;

/**
 * Mutable fields on an existing listing. Both can be null (caller may want to change
 * just one). The service applies whichever are present.
 */
public record UpdateListingCommand(BigDecimal askingPrice, ListingStatus status) {
}
