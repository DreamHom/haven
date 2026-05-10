package com.dreamhomes.haven.review;

import com.dreamhomes.haven.admin.AdminAction;
import com.dreamhomes.haven.admin.AdminAuditApi;
import com.dreamhomes.haven.admin.AuditTargetType;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingResponse;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.offer.OfferService;
import com.dreamhomes.haven.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ListingReviewRepository reviewRepository;
    @Mock ListingService listingService;
    @Mock OfferService offerService;
    @Mock NotificationApi notificationApi;
    @Mock AdminAuditApi adminAuditApi;

    ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, listingService, offerService, notificationApi, adminAuditApi);
    }

    @Test
    void ownerReviewsApplicantAfterAcceptedOfferAndListingClosed() {
        when(listingService.findById(7L)).thenReturn(closedListing(7L, /*ownerId=*/50L));
        when(offerService.hadAcceptedOffer(7L, /*revieweeId=*/100L)).thenReturn(true);
        when(reviewRepository.existsByListingIdAndReviewerUserIdAndRevieweeUserId(7L, 50L, 100L))
                .thenReturn(false);
        when(reviewRepository.save(any(ListingReview.class))).thenAnswer(inv -> {
            ListingReview r = inv.getArgument(0);
            r.setId(123L);
            return r;
        });

        ListingReview saved = service.post(/*reviewerId=*/50L, 7L, /*revieweeId=*/100L,
                (short) 5, "Smooth deal");

        assertThat(saved.getId()).isEqualTo(123L);

        ArgumentCaptor<ListingReview> cap = ArgumentCaptor.forClass(ListingReview.class);
        verify(reviewRepository).save(cap.capture());
        assertThat(cap.getValue().getReviewerUserId()).isEqualTo(50L);
        assertThat(cap.getValue().getRevieweeUserId()).isEqualTo(100L);
        assertThat(cap.getValue().getRating()).isEqualTo((short) 5);

        verify(notificationApi).recordSync(eq(NotificationKind.REVIEW_RECEIVED), eq(100L), any());
    }

    @Test
    void applicantReviewsOwnerAfterAcceptedOfferAndListingClosed() {
        when(listingService.findById(7L)).thenReturn(closedListing(7L, /*ownerId=*/50L));
        when(offerService.hadAcceptedOffer(7L, /*reviewerId=*/100L)).thenReturn(true);
        when(reviewRepository.save(any(ListingReview.class))).thenAnswer(inv -> inv.getArgument(0));

        service.post(/*reviewerId=*/100L, 7L, /*revieweeId=*/50L, (short) 4, "Friendly owner");

        verify(reviewRepository).save(any(ListingReview.class));
    }

    @Test
    void rejectsReviewWhenListingIsStillLive() {
        when(listingService.findById(7L)).thenReturn(liveListing(7L, 50L));

        assertThatThrownBy(() -> service.post(50L, 7L, 100L, (short) 5, "anything"))
                .isInstanceOf(ListingNotClosedException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsReviewByNonParticipant() {
        when(listingService.findById(7L)).thenReturn(closedListing(7L, /*ownerId=*/50L));
        when(offerService.hadAcceptedOffer(7L, 200L)).thenReturn(false);

        assertThatThrownBy(() -> service.post(/*reviewerId=*/200L, 7L, 100L, (short) 5, "x"))
                .isInstanceOf(NotADealParticipantException.class);
    }

    @Test
    void rejectsReviewWhenRevieweeIsNotTheCounterparty() {
        // Reviewer is the owner; reviewee should be an accepted-offer applicant. Supplied
        // reviewee never had an accepted offer on this listing.
        when(listingService.findById(7L)).thenReturn(closedListing(7L, /*ownerId=*/50L));
        when(offerService.hadAcceptedOffer(7L, /*revieweeId=*/200L)).thenReturn(false);

        assertThatThrownBy(() -> service.post(50L, 7L, 200L, (short) 5, "x"))
                .isInstanceOf(InvalidRevieweeException.class);
    }

    @Test
    void rejectsApplicantReviewingSomeoneOtherThanTheOwner() {
        when(listingService.findById(7L)).thenReturn(closedListing(7L, /*ownerId=*/50L));
        when(offerService.hadAcceptedOffer(7L, /*reviewerId=*/100L)).thenReturn(true);

        assertThatThrownBy(() -> service.post(/*reviewerId=*/100L, 7L, /*revieweeId=*/999L, (short) 5, "x"))
                .isInstanceOf(InvalidRevieweeException.class);
    }

    @Test
    void rejectsSelfReview() {
        assertThatThrownBy(() -> service.post(50L, 7L, /*revieweeId=*/50L, (short) 5, "x"))
                .isInstanceOf(InvalidRevieweeException.class);

        verify(listingService, never()).findById(any());
    }

    @Test
    void rejectsRatingOutOfRange() {
        assertThatThrownBy(() -> service.post(50L, 7L, 100L, (short) 6, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.post(50L, 7L, 100L, (short) 0, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankBody() {
        assertThatThrownBy(() -> service.post(50L, 7L, 100L, (short) 5, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateReviewByTheSameReviewerOnSameCounterpartyForSameListing() {
        when(listingService.findById(7L)).thenReturn(closedListing(7L, 50L));
        when(offerService.hadAcceptedOffer(7L, 100L)).thenReturn(true);
        when(reviewRepository.existsByListingIdAndReviewerUserIdAndRevieweeUserId(7L, 50L, 100L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.post(50L, 7L, 100L, (short) 5, "x"))
                .isInstanceOf(DuplicateReviewException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsReviewOnNonExistentListing() {
        when(listingService.findById(404L)).thenThrow(new ListingNotFoundException(404L));

        assertThatThrownBy(() -> service.post(50L, 404L, 100L, (short) 5, "x"))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void aggregateForUserReturnsEmptyWhenNoReviews() {
        when(reviewRepository.aggregateForUser(50L)).thenReturn(new ReviewAggregate(null, 0L));

        ReviewAggregate agg = service.aggregateForUser(50L);

        assertThat(agg.count()).isZero();
        assertThat(agg.averageRating()).isNull();
    }

    @Test
    void aggregateForUserReturnsNonNullWhenReviewsExist() {
        when(reviewRepository.aggregateForUser(50L)).thenReturn(new ReviewAggregate(4.5, 12L));

        ReviewAggregate agg = service.aggregateForUser(50L);

        assertThat(agg.averageRating()).isEqualTo(4.5);
        assertThat(agg.count()).isEqualTo(12L);
    }

    @Test
    void aggregateForUserCoercesNullRepoResultToEmpty() {
        when(reviewRepository.aggregateForUser(50L)).thenReturn(null);

        ReviewAggregate agg = service.aggregateForUser(50L);

        assertThat(agg.count()).isZero();
    }

    // ============================ takedown ============================

    @Test
    void authorCanDeleteTheirOwnReview() {
        ListingReview r = activeReview(123L, /*reviewerId=*/50L, /*revieweeId=*/100L);
        when(reviewRepository.findById(123L)).thenReturn(Optional.of(r));

        service.delete(/*callerId=*/50L, Role.OWNER, 123L, "Wrote it in anger");

        ArgumentCaptor<ListingReview> cap = ArgumentCaptor.forClass(ListingReview.class);
        verify(reviewRepository).save(cap.capture());
        assertThat(cap.getValue().isDeleted()).isTrue();
        assertThat(cap.getValue().getDeletedByUserId()).isEqualTo(50L);
        assertThat(cap.getValue().getDeletionReason()).isEqualTo("Wrote it in anger");
        // No admin audit row when the author self-deletes — only admin moderation gets logged.
        verify(adminAuditApi, never()).record(anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void adminCanTakedownAnyReviewAndWritesAuditLog() {
        ListingReview r = activeReview(123L, /*reviewerId=*/50L, /*revieweeId=*/100L);
        when(reviewRepository.findById(123L)).thenReturn(Optional.of(r));

        service.delete(/*adminId=*/1L, Role.ADMIN, 123L, "Defamatory");

        verify(reviewRepository).save(any(ListingReview.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metaCap = ArgumentCaptor.forClass(Map.class);
        verify(adminAuditApi).record(eq(1L), eq(AdminAction.REVIEW_TAKEDOWN),
                eq(AuditTargetType.REVIEW), eq(123L), metaCap.capture());
        assertThat(metaCap.getValue()).containsEntry("reason", "Defamatory");
    }

    @Test
    void revieweeCannotDeleteAReviewAboutThemselves() {
        // Letting the bad-rating recipient self-takedown would defeat the trust signal.
        ListingReview r = activeReview(123L, /*reviewerId=*/50L, /*revieweeId=*/100L);
        when(reviewRepository.findById(123L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.delete(/*callerId=*/100L, Role.OWNER, 123L, "any"))
                .isInstanceOf(NotAuthorisedToDeleteReviewException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void randomUserCannotDeleteSomeoneElsesReview() {
        ListingReview r = activeReview(123L, 50L, 100L);
        when(reviewRepository.findById(123L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.delete(/*callerId=*/200L, Role.APPLICANT, 123L, "any"))
                .isInstanceOf(NotAuthorisedToDeleteReviewException.class);
    }

    @Test
    void deleteRejectsAlreadyDeletedReview() {
        ListingReview r = activeReview(123L, 50L, 100L);
        r.setDeletedAt(Instant.now());
        r.setDeletedByUserId(1L);
        when(reviewRepository.findById(123L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.delete(50L, Role.OWNER, 123L, "any"))
                .isInstanceOf(ReviewAlreadyDeletedException.class);
    }

    @Test
    void deleteRejectsMissingReason() {
        assertThatThrownBy(() -> service.delete(50L, Role.OWNER, 123L, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewRepository, never()).findById(any());
    }

    @Test
    void deleteRejectsNonExistentReview() {
        when(reviewRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(50L, Role.ADMIN, 404L, "any"))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    private static ListingReview activeReview(Long id, Long reviewerId, Long revieweeId) {
        return ListingReview.builder()
                .id(id).listingId(7L)
                .reviewerUserId(reviewerId).revieweeUserId(revieweeId)
                .rating((short) 4).body("body")
                .createdAt(Instant.now()).build();
    }

    private static ListingResponse closedListing(Long id, Long ownerId) {
        return listing(id, ownerId, ListingStatus.CLOSED);
    }

    private static ListingResponse liveListing(Long id, Long ownerId) {
        return listing(id, ownerId, ListingStatus.LIVE);
    }

    private static ListingResponse listing(Long id, Long ownerId, ListingStatus status) {
        Instant now = Instant.now();
        return new ListingResponse(id, 1L, ownerId, ListingType.SALE,
                new BigDecimal("80000000.00"), "NGN", null, null, null,
                status, null, 0L, now, now, null);
    }
}
