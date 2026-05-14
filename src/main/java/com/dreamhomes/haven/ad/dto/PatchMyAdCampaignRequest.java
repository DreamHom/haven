package com.dreamhomes.haven.ad.dto;

import com.dreamhomes.haven.ad.model.AdCampaignStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial update for the sponsor's own draft campaign, or submit for review.")
public record PatchMyAdCampaignRequest(
        @Size(max = 255)
        String title,

        String body,

        @Min(0)
        Long budgetCents,

        @Schema(description = "Set to `PENDING_REVIEW` to submit a `DRAFT` campaign for admin review.")
        AdCampaignStatus status
) {

    @AssertTrue(message = "at least one field must be provided")
    public boolean hasAnyField() {
        return title != null || body != null || budgetCents != null || status != null;
    }
}
