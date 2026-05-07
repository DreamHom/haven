package com.dreamhomes.haven.review;

/**
 * JPQL projection — "average rating + count" for a reviewee. {@code averageRating} is
 * null when count = 0 (Hibernate maps {@code AVG} of an empty set to null). Callers
 * decide how to render that on the wire.
 */
public record ReviewAggregate(Double averageRating, Long count) {

    /** Empty aggregate (no reviews yet) — convenience for the no-reviews case. */
    public static ReviewAggregate empty() {
        return new ReviewAggregate(null, 0L);
    }
}
