package com.dreamhomes.haven.promotion.dto;

public record PromotionMetricsSummaryResponse(
        long totalActivePromotions,
        long totalImpressions,
        long totalClicks,
        double averageClickThroughRate
) {
}
