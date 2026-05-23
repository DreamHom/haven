package com.dreamhomes.haven.admin.dto;

import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.property.dto.PropertyResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin-only read model for a listing hidden from public browse (e.g. TAKEN_DOWN).")
public record ListingModerationSnapshotResponse(
        @Schema(description = "Full listing row including status and embedded property summary.")
        ListingResponse listing,
        @Schema(description = "Full property record (not just the browse-card summary).")
        PropertyResponse property,
        @Schema(description = "Number of gallery photos attached to the listing.")
        long photoCount,
        @Schema(description = "Most recent LISTING_TAKEDOWN audit entry, if one exists.")
        ListingTakedownAuditSnippet lastTakedown
) {
}
