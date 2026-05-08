package com.dreamhomes.haven.listing;

import java.time.Instant;
import java.util.Optional;

/**
 * Public contract for the listing feature. Cross-feature consumers wire this interface —
 * they never see the {@code Listing} entity, the repository, or any other implementation
 * detail. The implementation is {@code com.dreamhomes.haven.listing.ListingService} in
 * {@code feature-listing-impl}.
 *
 * <p>Listings are referenced by 7+ features (offer, comment, agentlisting, photo, review,
 * inspection, engagement, admin), so this interface is deliberately read-heavy. Write
 * methods exist only for state transitions that other features legitimately need to
 * trigger (admin approval, admin takedown).
 */
public interface ListingApi {

    /** Read for cross-feature consumers; throws {@link ListingNotFoundException} on miss. */
    ListingResponse findById(Long listingId);

    /** Returns the owner user id, or empty if the listing does not exist. */
    Optional<Long> ownerOf(Long listingId);

    /** Returns the status, or empty if the listing does not exist. */
    Optional<ListingStatus> statusOf(Long listingId);

    /** Convenience: ownership predicate combining the lookup + owner equality. */
    boolean isOwnedBy(Long listingId, Long userId);

    /** Existence check (lighter than findById when the data isn't needed). */
    boolean exists(Long listingId);

    /**
     * Stamp {@code approvedAt} on a listing — admin verified-listing badge per PRD §4.1.
     * Throws {@link ListingNotFoundException} if the listing has been deleted.
     */
    void markApproved(Long listingId, Instant when);

    /**
     * Force a status transition without going through the owner-driven rules in
     * {@code ListingService.update} — used by admin takedown (PRD §4.10) which is the
     * platform's safety net. Throws {@link ListingNotFoundException} on miss.
     */
    void forceStatus(Long listingId, ListingStatus status, Instant when);
}
