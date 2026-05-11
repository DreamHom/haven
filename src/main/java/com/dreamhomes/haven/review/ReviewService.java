package com.dreamhomes.haven.review;

import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.AdminAuditApi;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.offer.OfferService;
import com.dreamhomes.haven.user.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.review.dto.ReviewAggregate;
import com.dreamhomes.haven.review.exception.DuplicateReviewException;
import com.dreamhomes.haven.review.exception.InvalidRevieweeException;
import com.dreamhomes.haven.review.exception.ListingNotClosedException;
import com.dreamhomes.haven.review.exception.NotADealParticipantException;
import com.dreamhomes.haven.review.exception.NotAuthorisedToDeleteReviewException;
import com.dreamhomes.haven.review.exception.ReviewAlreadyDeletedException;
import com.dreamhomes.haven.review.exception.ReviewNotFoundException;
import com.dreamhomes.haven.user.model.User;

/**
 * Post-deal reviews. The data layer enforces uniqueness per (listing, reviewer,
 * reviewee) and rating bounds; the service enforces participant identity + listing
 * lifecycle.
 *
 * <p>All cross-aggregate access goes through APIs only:
 * <ul>
 *   <li>{@link ListingService} — status check + ownership.</li>
 *   <li>{@link OfferService} — accepted-offer eligibility for review participation.</li>
 *   <li>{@link NotificationApi} — sync notification to the reviewee.</li>
 *   <li>{@link AdminAuditApi} — audit row when an admin takes a review down.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {

    private final ListingReviewRepository reviewRepository;
    private final ListingService listingService;
    private final OfferService offerService;
    private final NotificationApi notificationApi;
    private final AdminAuditApi adminAuditApi;

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

        // Throws ListingNotFoundException if missing.
        ListingResponse listing = listingService.findById(listingId);
        if (listing.status() != ListingStatus.CLOSED) {
            throw new ListingNotClosedException(listingId);
        }

        // Participant + counterparty validation.
        boolean reviewerIsOwner = listing.ownerId().equals(reviewerId);
        if (reviewerIsOwner) {
            if (!offerService.hadAcceptedOffer(listingId, revieweeId)) {
                throw new InvalidRevieweeException();
            }
        } else {
            if (!offerService.hadAcceptedOffer(listingId, reviewerId)) {
                throw new NotADealParticipantException();
            }
            if (!listing.ownerId().equals(revieweeId)) {
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
     * Soft-delete a review. Authorisation: the review's author OR an admin. Admin
     * deletes write an {@code admin_audit_log} row through {@link AdminAuditApi}.
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
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("listingId", review.getListingId());
            metadata.put("reviewerUserId", review.getReviewerUserId());
            metadata.put("revieweeUserId", review.getRevieweeUserId());
            metadata.put("reason", reason);
            adminAuditApi.record(callerId, AdminAction.REVIEW_TAKEDOWN,
                    AuditTargetType.REVIEW, review.getId(), metadata);
        }

        log.info("Caller {} ({}) deleted reviewId={} reason='{}'",
                callerId, callerRole, reviewId, reason);
        return review;
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
        notificationApi.recordSync(NotificationKind.REVIEW_RECEIVED, recipientId, payload);
    }
}
