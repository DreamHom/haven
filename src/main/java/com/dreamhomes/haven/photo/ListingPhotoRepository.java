package com.dreamhomes.haven.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, Long> {

    /** Backs the public detail-page photo gallery. Ordered by (display_order, id). */
    List<ListingPhoto> findByListingIdOrderByDisplayOrderAscIdAsc(Long listingId);

    /**
     * Highest existing display_order for this listing. {@code COALESCE} via the JPQL
     * doesn't quite work the same as SQL — return null when there are no photos and
     * let the service interpret that as "start at 0." One query, no entity load.
     */
    @Query("SELECT MAX(p.displayOrder) FROM ListingPhoto p WHERE p.listingId = :listingId")
    Integer findMaxDisplayOrderForListing(@Param("listingId") Long listingId);
}
