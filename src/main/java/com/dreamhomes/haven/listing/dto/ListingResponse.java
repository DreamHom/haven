package com.dreamhomes.haven.listing.dto;

import com.dreamhomes.haven.property.dto.PropertySummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;

/**
 * Public response shape for a listing. Embeds {@link PropertySummary} for browse cards.
 * Construction lives in {@code feature-listing-impl} (no entity references in this DTO).
 *
 * <p><b>Trust signals (Item 16, post-session-tasks.md).</b> Two fields drive Vista's
 * trust-signal chips so the frontend never needs an N+1 fetch to render them:
 * <ul>
 *   <li>{@link #ownerIdentityVerifiedAt()} — the listing owner's identity verification.
 *       Null means the owner has not completed identity verification; render a "⚠️
 *       Possible Scam" warning chip on the card.</li>
 *   <li>{@code property.documentsVerifiedAt} (on {@link PropertySummary}) — the
 *       property-document verification. Non-null means an admin has approved the
 *       title/registry docs; render a "✓ Verified" badge.</li>
 * </ul>
 */
public record ListingResponse(
        Long id,
        Long propertyId,
        Long ownerId,
        ListingType listingType,
        BigDecimal askingPrice,
        String currency,
        BigDecimal cautionFee,
        BigDecimal serviceCharge,
        BigDecimal agencyFee,
        String title,
        String description,
        String headline,
        LocalDate handoverDate,
        String virtualTourUrl,
        boolean priceNegotiable,
        ListingStatus status,
        Instant approvedAt,
        Long viewCount,
        Instant createdAt,
        Instant updatedAt,
        PropertySummary property,
        Long assignedAgentId,
        Long pendingReportCount,
        String petsAllowed,
        String utilitiesNote,
        String floorPlanUrl,
        String ownerPublicBio,
        @Schema(
                description = """
                        Owner's identity-verification timestamp. **Null = owner has not \
                        completed identity verification.** UI should render a "⚠️ Possible \
                        Scam" warning chip on listings where this is null. Non-null = owner \
                        is verified; render no special owner-side signal (the absence-of-warning \
                        IS the signal).
                        """,
                example = "2026-04-12T10:00:00Z",
                nullable = true)
        Instant ownerIdentityVerifiedAt
) {
}
