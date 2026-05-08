package com.dreamhomes.haven.review;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.admin.AdminAction;
import com.dreamhomes.haven.admin.AdminAuditLog;
import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.admin.AuditTargetType;
import com.dreamhomes.haven.notification.Notification;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.notification.NotificationSource;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.offer.OfferStatus;
import com.dreamhomes.haven.user.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Post-deal reviews. Reviews are immutable in Phase 10 — admins can't take them down
 * yet, authors can't edit. The data layer enforces uniqueness per (listing, reviewer,
 * reviewee) and rating bounds; the service enforces participant identity + listing
 * lifecycle.
 *
 * <p>Participant rules:
 * <ul>
 *   <li>Reviewer is the listing's owner → reviewee must be an applicant who had an
 *       ACCEPTED offer on this listing.</li>
 *   <li>Reviewer had an ACCEPTED offer on the listing → reviewee must be the listing's
 *       owner.</li>
 * </ul>
 * Anything else surfaces as 403 — never leak which condition failed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {

    private final ListingReviewRepository reviewRepository;
    private final ListingRepository listingRepository;
    private final OfferRepository offerRepository;
    private final NotificationRepository notificationRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ListingReview post(Long reviewerId, Long listingId, Long revieweeId,
                              short rating, String body) {
        if (reviewerId.equals(revieweeId)) {
            throw new InvalidRevieweeException();
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5 inclusive");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Review body cannot be empty");
        }

        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (listing.getStatus() != ListingStatus.CLOSED) {
            throw new ListingNotClosedException(listingId);
        }

        // Participant + counterparty validation. Two paths — short-circuit on reviewer
        // role so each path only makes the DB calls it needs:
        //   reviewer = owner → reviewee must have an ACCEPTED offer.
        //   reviewer had an ACCEPTED offer → reviewee must be the listing's owner.
        boolean reviewerIsOwner = listing.getOwnerId().equals(reviewerId);
        if (reviewerIsOwner) {
            boolean revieweeIsAcceptedApplicant = offerRepository
                    .existsByListingIdAndApplicantIdAndStatus(listingId, revieweeId, OfferStatus.ACCEPTED);
            if (!revieweeIsAcceptedApplicant) {
                throw new InvalidRevieweeException();
            }
        } else {
            boolean reviewerHadAcceptedOffer = offerRepository
                    .existsByListingIdAndApplicantIdAndStatus(listingId, reviewerId, OfferStatus.ACCEPTED);
            if (!reviewerHadAcceptedOffer) {
                throw new NotADealParticipantException();
            }
            if (!listing.getOwnerId().equals(revieweeId)) {
                throw new InvalidRevieweeException();
            }
        }

        if (reviewRepository.existsByListingIdAndReviewerUserIdAndRevieweeUserId(
                listingId, reviewerId, revieweeId)) {
            throw new DuplicateReviewException();
        }

        Instant now = Instant.now();
        ListingReview saved = reviewRepository.save(ListingReview.builder()
                .listingId(listingId)
                .reviewerUserId(reviewerId)
                .revieweeUserId(revieweeId)
                .rating(rating)
                .body(body.trim())
                .createdAt(now)
                .build());

        notifyReviewee(revieweeId, saved);

        log.info("User {} reviewed user {} on listing {} with rating {} → reviewId={}",
                reviewerId, revieweeId, listingId, rating, saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ListingReview> listForReviewee(Long revieweeId, Pageable pageable) {
        return reviewRepository.findByRevieweeUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                revieweeId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ListingReview> listForListing(Long listingId, Pageable pageable) {
        return reviewRepository.findByListingIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                listingId, pageable);
    }

    /**
     * Soft-delete a review (Phase 12). Authorisation: the review's author OR an admin.
     * Mirrors {@code CommentService.delete} — author can self-cancel; admin moderation
     * goes through the same call and additionally writes an {@code admin_audit_log} row
     * with action {@link AdminAction#REVIEW_TAKEDOWN}. Reason is required for audit.
     */
    @Transactional
    public ListingReview delete(Long callerId, Role callerRole, Long reviewId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Deletion reason is required");
        }
        ListingReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));
        if (review.isDeleted()) {
            throw new ReviewAlreadyDeletedException(reviewId);
        }

        boolean isAdmin = callerRole == Role.ADMIN;
        boolean isAuthor = callerId.equals(review.getReviewerUserId());
        if (!isAdmin && !isAuthor) {
            throw new NotAuthorisedToDeleteReviewException();
        }

        review.setDeletedAt(Instant.now());
        review.setDeletedByUserId(callerId);
        review.setDeletionReason(reason);
        reviewRepository.save(review);

        if (isAdmin) {
            recordAdminAudit(callerId, review, reason);
        }

        log.info("Caller {} ({}) deleted reviewId={} reason='{}'",
                callerId, callerRole, reviewId, reason);
        return review;
    }

    private void recordAdminAudit(Long adminId, ListingReview review, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("listingId", review.getListingId());
        metadata.put("reviewerUserId", review.getReviewerUserId());
        metadata.put("revieweeUserId", review.getRevieweeUserId());
        metadata.put("reason", reason);
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(AdminAction.REVIEW_TAKEDOWN)
                .targetType(AuditTargetType.REVIEW)
                .targetId(review.getId())
                .metadata(serialize(metadata))
                .createdAt(Instant.now())
                .build());
    }

    @Transactional(readOnly = true)
    public ReviewAggregate aggregateForUser(Long userId) {
        ReviewAggregate agg = reviewRepository.aggregateForUser(userId);
        return agg == null ? ReviewAggregate.empty() : agg;
    }

    private void notifyReviewee(Long recipientId, ListingReview review) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewId", review.getId());
        payload.put("listingId", review.getListingId());
        payload.put("reviewerUserId", review.getReviewerUserId());
        payload.put("rating", review.getRating());
        notificationRepository.save(Notification.builder()
                .recipientId(recipientId)
                .kind(NotificationKind.REVIEW_RECEIVED)
                .source(NotificationSource.SYNC)
                .payload(serialize(payload))
                .createdAt(Instant.now())
                .build());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise review notification payload", e);
        }
    }
}
