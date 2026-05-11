package com.dreamhomes.haven.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dreamhomes.haven.review.dto.ReviewAggregate;

public interface ListingReviewRepository extends JpaRepository<ListingReview, Long> {

    /** Pre-flight check the service runs to short-circuit the duplicate path with a clean 409. */
    boolean existsByListingIdAndReviewerUserIdAndRevieweeUserId(
            Long listingId, Long reviewerUserId, Long revieweeUserId);

    /**
     * Public read on a user's profile: every active review about them, newest first.
     * Backed by the V17 partial index {@code listing_reviews_active_reviewee_idx}.
     */
    Page<ListingReview> findByRevieweeUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long revieweeUserId, Pageable pageable);

    /**
     * Public read on a listing's review feed (active only). Backed by the V17 partial
     * index {@code listing_reviews_active_listing_idx}.
     */
    Page<ListingReview> findByListingIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long listingId, Pageable pageable);

    /**
     * Aggregate: "for this user, what's the average rating + how many reviews?"
     * Excludes soft-deleted reviews so a takedown drops the row's effect on the
     * average + count immediately. Single query so the public profile load doesn't
     * fan out to two GETs.
     */
    @Query("SELECT new com.dreamhomes.haven.review.dto.ReviewAggregate("
            + "AVG(r.rating), COUNT(r)) "
            + "FROM ListingReview r "
            + "WHERE r.revieweeUserId = :userId AND r.deletedAt IS NULL")
    ReviewAggregate aggregateForUser(@Param("userId") Long userId);
}
