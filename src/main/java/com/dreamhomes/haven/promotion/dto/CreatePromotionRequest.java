package com.dreamhomes.haven.promotion.dto;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;


public record CreatePromotionRequest(
        @NotNull 
        PromotionTargetType targetType,
        
        @NotNull 
        Long targetId,
        
        @NotNull 
        PromotionPlacement placement,
        
        @NotNull 
        Instant startsAt,
        
        @NotNull 
        Instant endsAt
) {
}
