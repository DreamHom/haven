package com.dreamhomes.haven.ad.dto;

import com.dreamhomes.haven.ad.model.AdCampaignStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin status change for an ad campaign.")
public record AdminPatchAdCampaignRequest(
        @NotNull
        AdCampaignStatus status
) {
}
