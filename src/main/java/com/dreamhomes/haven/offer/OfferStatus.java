package com.dreamhomes.haven.offer;

/**
 * Lifecycle of an offer.
 * <ul>
 *   <li>{@link #PENDING} — applicant submitted, owner hasn't acted.</li>
 *   <li>{@link #ACCEPTED} — owner accepted. Terminal.</li>
 *   <li>{@link #DECLINED} — owner declined. Terminal.</li>
 * </ul>
 *
 * <p>Counter-offer is out of scope for now — accepting or declining is a one-shot
 * decision that closes the offer.
 */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
