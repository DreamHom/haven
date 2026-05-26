package com.dreamhomes.haven.promotion.dto;

import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import com.dreamhomes.haven.user.dto.PublicUserProfile;

public record PromotionPublicResponse(
        Long promotionId,
        PromotionTargetType targetType,
        Long targetId,
        PromotionPlacement placement,
        String label,
        ListingResponse listing,
        PublicUserProfile agent
) {
}
