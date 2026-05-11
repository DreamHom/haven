package com.dreamhomes.haven.offer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.dreamhomes.haven.review.ReviewService;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.model.OfferStatus;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    /**
     * Backs {@code GET /api/offers/mine}: every offer where the caller is either the
     * applicant who submitted it OR the owner who received it. The persona audit
     * (Temi, Biodun) flagged this as the single biggest "I made an offer and lost
     * the thread" gap.
     */
    Page<Offer> findByApplicantIdOrOwnerIdOrderByCreatedAtDesc(
            Long applicantId, Long ownerId, Pageable pageable);

    /**
     * "Was this applicant the buyer/renter on this listing?" — used by ReviewService to
     * gate post-deal reviews on participant identity. An ACCEPTED offer is the canonical
     * signal that the deal happened.
     */
    boolean existsByListingIdAndApplicantIdAndStatus(Long listingId, Long applicantId, OfferStatus status);

    /** Aggregate count by status — backs the admin analytics summary. */
    long countByStatus(OfferStatus status);

    /**
     * All other offers on the same listing in the given status, excluding the offer
     * passed by id. Backs the auto-decline-on-accept flow: when one offer wins, every
     * other PENDING sibling on the listing flips to DECLINED so the negotiation queue
     * doesn't carry stale rows that an applicant could still try to act on.
     */
    List<Offer> findByListingIdAndStatusAndIdNot(Long listingId, OfferStatus status, Long excludeId);
}
