package com.dreamhomes.haven.offer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    /**
     * "Was this applicant the buyer/renter on this listing?" — used by ReviewService to
     * gate post-deal reviews on participant identity. An ACCEPTED offer is the canonical
     * signal that the deal happened.
     */
    boolean existsByListingIdAndApplicantIdAndStatus(Long listingId, Long applicantId, OfferStatus status);
}
