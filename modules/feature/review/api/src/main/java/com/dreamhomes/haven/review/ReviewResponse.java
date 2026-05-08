package com.dreamhomes.haven.review;

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
