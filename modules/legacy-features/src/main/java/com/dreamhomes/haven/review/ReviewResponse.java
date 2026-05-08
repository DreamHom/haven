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
    public static ReviewResponse from(ListingReview r) {
        return new ReviewResponse(
                r.getId(), r.getListingId(),
                r.getReviewerUserId(), r.getRevieweeUserId(),
                r.getRating(), r.getBody(), r.getCreatedAt());
    }
}
