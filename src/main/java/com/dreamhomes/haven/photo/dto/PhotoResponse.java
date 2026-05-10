package com.dreamhomes.haven.photo.dto;

import java.time.Instant;

public record PhotoResponse(
        Long id,
        Long listingId,
        String url,
        Integer displayOrder,
        String caption,
        Instant uploadedAt
) {
}
