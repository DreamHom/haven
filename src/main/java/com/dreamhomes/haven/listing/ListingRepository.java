package com.dreamhomes.haven.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    /** Backs the public browse endpoint — only LIVE listings are visible to anonymous callers. */
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);
}
