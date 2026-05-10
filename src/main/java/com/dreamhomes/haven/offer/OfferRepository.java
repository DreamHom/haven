package com.dreamhomes.haven.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.review.ReviewService;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.model.OfferStatus;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    /**
     * "Was this applicant the buyer/renter on this listing?" — used by ReviewService to
     * gate post-deal reviews on participant identity. An ACCEPTED offer is the canonical
     * signal that the deal happened.
     */
    boolean existsByListingIdAndApplicantIdAndStatus(Long listingId, Long applicantId, OfferStatus status);
}
