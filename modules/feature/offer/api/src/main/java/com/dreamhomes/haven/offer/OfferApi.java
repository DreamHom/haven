package com.dreamhomes.haven.offer;

/**
 * Public contract for the offer feature, scoped to what cross-feature consumers actually
 * need. The full feature (submit / counter / respond) is exercised through the REST
 * controller; this interface only exposes the cross-aggregate read review uses.
 */
public interface OfferApi {

    /**
     * Did the given applicant ever have an ACCEPTED offer on this listing? Used by the
     * review feature to gate "post-deal review" eligibility.
     */
    boolean hadAcceptedOffer(Long listingId, Long applicantUserId);
}
