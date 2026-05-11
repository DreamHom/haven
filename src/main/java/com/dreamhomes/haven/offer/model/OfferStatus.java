package com.dreamhomes.haven.offer.model;

/**
 * Lifecycle of an offer.
 * <ul>
 *   <li>{@link #PENDING} — proposer submitted, the OTHER party hasn't acted yet.</li>
 *   <li>{@link #ACCEPTED} — terminal; the deal is on (closes the chain).</li>
 *   <li>{@link #DECLINED} — terminal; the chain ends without a deal.</li>
 *   <li>{@link #COUNTERED} — terminal-for-this-row but tracked; replaced by a child
 *       offer with {@code parent_offer_id} pointing back. The chain continues on the
 *       child until someone hits ACCEPTED or DECLINED. Phase 13.</li>
 * </ul>
 */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    COUNTERED
}
