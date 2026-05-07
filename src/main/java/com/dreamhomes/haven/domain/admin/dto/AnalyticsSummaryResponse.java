package com.dreamhomes.haven.domain.admin.dto;

public record AnalyticsSummaryResponse(
        long users,
        long properties,
        long listings,
        long offers,
        long inspections
) {}

