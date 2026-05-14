package com.dreamhomes.haven.ad.dto;

import com.dreamhomes.haven.ad.model.AdCampaignStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Sponsor or admin view of an ad campaign row.")
public record AdCampaignResponse(
        Long id,
        Long sponsorUserId,
        String title,
        String body,
        AdCampaignStatus status,
        Long budgetCents,
        Instant createdAt,
        Instant updatedAt
) {
}
