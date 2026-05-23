package com.dreamhomes.haven.lead.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Lead row for listing owners / admins. Contact fields are null until revealed.")
public record ListingLeadResponse(
        Long id,
        Long listingId,
        Long applicantUserId,
        String message,
        Instant createdAt,
        boolean revealed,
        String contactPhone,
        String contactEmail
) {
}
