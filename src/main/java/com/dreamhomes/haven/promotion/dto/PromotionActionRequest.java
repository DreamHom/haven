package com.dreamhomes.haven.promotion.dto;

import jakarta.validation.constraints.NotBlank;

public record PromotionActionRequest(
        @NotBlank 
        String reason
) {
}
