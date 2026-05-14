package com.dreamhomes.haven.ad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Create a draft ad campaign owned by the authenticated user.")
public record CreateAdCampaignRequest(
        @NotBlank
        @Size(max = 255)
        String title,

        String body,

        @Schema(description = "Optional budget ceiling in minor currency units (e.g. kobo).")
        @Min(0)
        Long budgetCents
) {
}
