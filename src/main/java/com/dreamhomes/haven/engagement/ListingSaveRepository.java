package com.dreamhomes.haven.engagement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingSaveRepository extends JpaRepository<ListingSave, ListingSaveId> {

    /** Backs {@code GET /api/listings/saved/mine} — most-recent first. */
    Page<ListingSave> findByUserIdOrderBySavedAtDesc(Long userId, Pageable pageable);

    /** Aggregate count: "how many people saved this listing" — for an analytics card. */
    long countByListingId(Long listingId);

    boolean existsByUserIdAndListingId(Long userId, Long listingId);
}
