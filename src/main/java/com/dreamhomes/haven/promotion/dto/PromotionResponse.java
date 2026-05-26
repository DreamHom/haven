package com.dreamhomes.haven.promotion.dto;

import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;

import java.time.Instant;

public record PromotionResponse(
        Long id,
        PromotionTargetType targetType,
        Long targetId,
        PromotionPlacement placement,
        PromotionStatus status,
        Instant startsAt,
        Instant endsAt,
        Integer priority,
        Long createdByUserId,
        Long approvedByAdminId,
        Instant approvedAt,
        String decisionReason,
        Instant createdAt,
        Instant updatedAt
) {
}
