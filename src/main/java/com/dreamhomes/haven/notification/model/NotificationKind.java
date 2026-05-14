package com.dreamhomes.haven.notification.model;

import com.dreamhomes.haven.admin.service.AdminListingService;
import com.dreamhomes.haven.admin.service.AdminVerificationService;
import com.dreamhomes.haven.agentlisting.AgentListingService;
import com.dreamhomes.haven.comment.CommentService;
import com.dreamhomes.haven.offer.OfferService;
import com.dreamhomes.haven.review.ReviewService;
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
    /**
     * Sync — fired by {@link com.dreamhomes.haven.lead.ListingLeadService} when an applicant submits
     * interest on a live listing; recipient is the listing owner.
     * Payload JSON: {@code listingId} (number), {@code leadId} (number).
     */
    LISTING_LEAD_SUBMITTED,
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
    OFFER_COUNTERED,
    /**
     * Sync — fired by OfferService.respond when accepting one offer auto-declines its
     * PENDING siblings on the same listing. Recipient is the losing applicant; payload
     * carries {@code listingId} and {@code reason: "ANOTHER_OFFER_ACCEPTED"}.
     */
    OFFER_AUTO_DECLINED,
    /**
     * Sync — fired by {@code ListingReportService} when a user reports a listing. One row
     * per admin so the moderation queue surfaces new reports without polling.
     */
    LISTING_REPORTED,

    /** Sync — fired after admin resolves or dismisses a report; recipient is the reporter. */
    LISTING_REPORT_RESOLVED,

    /** Sync — fired on registration as a "welcome + next step" pointer. */
    WELCOME,

    /** Sync — fired immediately on verification submission so the submitter sees acknowledgement. */
    VERIFICATION_SUBMITTED,

    /** Sync — fired to the applicant immediately on inspection booking. */
    INSPECTION_BOOKED,

    /** Sync — fired to the applicant on offer submission so they have a confirmation in-platform. */
    OFFER_RECEIVED_BY_PLATFORM
}
