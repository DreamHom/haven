package com.dreamhomes.haven.listing.model;

/**
 * Lifecycle of a listing:
 * <ul>
 *   <li>{@link #LIVE} — visible to the public, accepts inspection requests and offers.</li>
 *   <li>{@link #PAUSED} — owner-paused (travelling, holding off); hidden from public browse.</li>
 *   <li>{@link #CLOSED} — terminal: owner-closed because the deal completed or was withdrawn. Cannot transition back.</li>
 *   <li>{@link #TAKEN_DOWN} — admin-driven removal for policy violation. Hidden from
 *       public browse like CLOSED, but reversible — an admin can re-publish it back to LIVE.</li>
 * </ul>
 */
public enum ListingStatus {
    LIVE,
    PAUSED,
    CLOSED,
    TAKEN_DOWN
}
