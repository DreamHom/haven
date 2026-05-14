package com.dreamhomes.haven.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ListingVideoRepository extends JpaRepository<ListingVideo, Long> {

    List<ListingVideo> findByListingIdOrderByDisplayOrderAscIdAsc(Long listingId);

    @Query("SELECT MAX(v.displayOrder) FROM ListingVideo v WHERE v.listingId = :listingId")
    Integer findMaxDisplayOrderForListing(@Param("listingId") Long listingId);
}
