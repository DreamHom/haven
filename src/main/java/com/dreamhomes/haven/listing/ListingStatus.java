package com.dreamhomes.haven.listing;

/**
 * Lifecycle of a listing:
 * <ul>
 *   <li>{@link #LIVE} — visible to the public, accepts inspection requests and offers.</li>
 *   <li>{@link #PAUSED} — owner-paused (travelling, holding off); hidden from public browse.</li>
 *   <li>{@link #CLOSED} — terminal: deal done or withdrawn. Cannot transition back.</li>
 * </ul>
 */
public enum ListingStatus {
    LIVE,
    PAUSED,
    CLOSED
}
