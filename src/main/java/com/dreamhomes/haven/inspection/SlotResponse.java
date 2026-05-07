package com.dreamhomes.haven.inspection;

import java.time.Instant;

public record SlotResponse(
        Long id,
        Long listingId,
        Instant startsAt,
        Instant endsAt
) {
    public static SlotResponse from(InspectionSlot s) {
        return new SlotResponse(s.getId(), s.getListingId(), s.getStartsAt(), s.getEndsAt());
    }
}
