package com.dreamhomes.haven.notification;

/**
 * The kinds of notifications we deliver. Stored as a string so adding a new kind is a
 * code-only change (no DB migration). The {@code payload} column holds kind-specific
 * JSON — different shapes per kind are by design.
 */
public enum NotificationKind {
    INSPECTION_REQUESTED,
    OFFER_SUBMITTED,
    /** Sync — fired by AdminVerificationService when an admin approves a submission. */
    VERIFICATION_APPROVED,
    /** Sync — fired by AdminVerificationService when an admin rejects a submission. */
    VERIFICATION_REJECTED,
    /** Sync — fired by AdminListingService when an admin grants the verified-listing badge. */
    LISTING_APPROVED,
    /** Sync — fired by AdminListingService when an admin takes a listing down. */
    LISTING_TAKEDOWN
}
