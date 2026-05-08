package com.dreamhomes.haven.inspection;

import java.time.Instant;

public record SlotResponse(
        Long id,
        Long listingId,
        Instant startsAt,
        Instant endsAt
) {
}
