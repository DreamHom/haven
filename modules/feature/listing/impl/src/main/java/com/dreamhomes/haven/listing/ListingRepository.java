package com.dreamhomes.haven.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    /** Backs the public browse endpoint — only LIVE listings are visible to anonymous callers. */
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    /**
     * Lock-free atomic increment of {@code view_count}. Bypasses Hibernate's first-level
     * cache + version check on purpose: a popular listing's @Version shouldn't churn on
     * every anonymous page view. Returns the number of rows updated (0 = listing missing).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Listing l SET l.viewCount = l.viewCount + 1 WHERE l.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
