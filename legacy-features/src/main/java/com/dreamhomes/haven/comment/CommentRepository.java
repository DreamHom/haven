package com.dreamhomes.haven.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Backs the public list endpoint — only non-deleted comments, oldest first so the
     * thread reads top-to-bottom. The partial index {@code comments_active_per_listing_idx}
     * makes this an index-only seek.
     */
    Page<Comment> findByListingIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long listingId, Pageable pageable);
}
