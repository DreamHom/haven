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
    LISTING_TAKEDOWN,
    /** Sync — fired by CommentService when a non-owner posts a comment on a listing. */
    COMMENT_POSTED,
    /** Sync — fired by AgentListingService when an owner invites an agent to manage a listing. */
    AGENT_ASSIGNMENT_REQUESTED,
    /** Sync — fired when the targeted agent accepts; recipient is the owner. */
    AGENT_ASSIGNMENT_ACCEPTED,
    /** Sync — fired when the targeted agent declines; recipient is the owner. */
    AGENT_ASSIGNMENT_DECLINED,
    /** Sync — fired when either party revokes an active assignment; recipient is the other party. */
    AGENT_ASSIGNMENT_REVOKED,
    /** Sync — fired by ReviewService when a review is posted; recipient is the reviewee. */
    REVIEW_RECEIVED,
    /** Sync — fired by OfferService.counter; recipient is the OTHER party in the negotiation. */
    OFFER_COUNTERED
}
