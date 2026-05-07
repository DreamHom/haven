package com.dreamhomes.haven.domain.inspection.dto;

import java.time.Instant;

public record InspectionSlotResponse(
        Long id,
        Long listingId,
        Long agentId,
        Instant startAt,
        Instant endAt
) {}

