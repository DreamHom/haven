package com.dreamhomes.haven.promotion.dto;

import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import jakarta.validation.constraints.NotNull;

public record PromotionTrackRequest(
        @NotNull 
        PromotionPlacement placement
) {
}
