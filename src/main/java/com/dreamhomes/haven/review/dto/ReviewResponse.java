package com.dreamhomes.haven.review.dto;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long listingId,
        Long reviewerUserId,
        Long revieweeUserId,
        Short rating,
        String body,
        Instant createdAt
) {
}
