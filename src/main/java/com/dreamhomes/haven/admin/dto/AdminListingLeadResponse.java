package com.dreamhomes.haven.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Admin read of a listing lead — contact fields always present for moderation.")
public record AdminListingLeadResponse(
        Long id,
        Long listingId,
        Long applicantUserId,
        String message,
        Instant createdAt,
        Instant revealedAt,
        String contactPhone,
        String contactEmail
) {
}
