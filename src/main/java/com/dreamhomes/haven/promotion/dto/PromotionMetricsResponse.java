package com.dreamhomes.haven.promotion.dto;

public record PromotionMetricsResponse(
        Long promotionId,
        long impressions,
        long clicks,
        double clickThroughRate
) {
}
