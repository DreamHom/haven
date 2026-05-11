package com.dreamhomes.haven.inspection.dto;

import java.time.Instant;

public record SlotResponse(
        Long id,
        Long listingId,
        Instant startsAt,
        Instant endsAt
) {
}
