package com.dreamhomes.haven.review;

/**
 * Public contract for the review feature, scoped down to what cross-feature consumers
 * legitimately need. The full feature (post / list / soft-delete) is exercised through
 * the REST controller; this interface only exists for the cross-feature aggregate read
 * that user profile pages depend on.
 *
 * <p>The implementation is {@code com.dreamhomes.haven.review.ReviewService}, currently
 * still in {@code legacy-features} until the review feature splits into its own module.
 * Until then, this thin {@code review-api} module exists so user-impl can compile
 * without depending on legacy-features.
 */
public interface ReviewApi {

    /**
     * Aggregate (average rating + count) of all non-deleted reviews <em>received</em>
     * by the given user. Returns {@link ReviewAggregate#empty()} when the user has no
     * reviews yet.
     */
    ReviewAggregate aggregateForUser(Long userId);
}
