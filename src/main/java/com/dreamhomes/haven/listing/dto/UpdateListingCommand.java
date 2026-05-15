package com.dreamhomes.haven.listing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.dreamhomes.haven.listing.model.ListingStatus;

/**
 * Mutable fields on an existing listing. All optional — the service applies whichever
 * are present.
 *
 * <p>Persona audit (Amaka, Biodun): in v1 the owner could only update price + status,
 * which made any typo in marketing copy permanent. PATCH now extends to title /
 * description / headline / handoverDate.</p>
 */
public record UpdateListingCommand(
        BigDecimal askingPrice,
        ListingStatus status,
        String title,
        String description,
        String headline,
        LocalDate handoverDate,
        String virtualTourUrl,
        Boolean priceNegotiable,
        String floorPlanUrl,
        String petsAllowed,
        String utilitiesNote) {
}
