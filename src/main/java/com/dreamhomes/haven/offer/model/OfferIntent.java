package com.dreamhomes.haven.offer.model;

/**
 * Applicant-declared intent on an offer. Optional on submission — null means
 * "unspecified" (which historic offers default to).
 *
 * <ul>
 *   <li>{@link #RENT} — straight tenancy.</li>
 *   <li>{@link #BUY} — outright purchase.</li>
 *   <li>{@link #RENT_TO_BUY} — rent now with intent to buy later; signals the
 *       owner to engage financing partners (Moniepoint etc.).</li>
 * </ul>
 *
 * <p>Persona audit (Ngozi): the whole point of her presence on the platform
 * is rent-to-buy; making it a first-class enum lets owners filter and route.</p>
 */
public enum OfferIntent {
    RENT,
    BUY,
    RENT_TO_BUY
}
