package com.dreamhomes.haven.listingreport.dto;

import com.dreamhomes.haven.listingreport.model.ListingReportStatus;
import com.dreamhomes.haven.listingreport.model.ReportReason;

import java.time.Instant;

/**
 * Admin projection of a listing report. Adds the moderation lifecycle fields
 * ({@code status}, {@code resolutionNote}, {@code resolvedByAdminId},
 * {@code resolvedAt}) the public reporter projection doesn't see.
 */
public record AdminListingReportResponse(
        Long id,
        Long listingId,
        Long reporterUserId,
        ReportReason reason,
        String details,
        ListingReportStatus status,
        String resolutionNote,
        Long resolvedByAdminId,
        Instant resolvedAt,
        Instant createdAt
) {
}
