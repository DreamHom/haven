package com.dreamhomes.haven.listingreport.dto;

import com.dreamhomes.haven.listingreport.model.ReportReason;

import java.time.Instant;

/**
 * Wire response for {@code POST /api/listings/{id}/report}. Reporter sees just enough
 * to know the report was recorded — no admin-internal fields.
 */
public record ListingReportResponse(
        Long id,
        Long listingId,
        ReportReason reason,
        String details,
        Instant createdAt
) {
}
