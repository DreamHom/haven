package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.offer.exception.InvalidOfferTransitionException;
import com.dreamhomes.haven.offer.exception.OfferNotFoundException;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.model.OfferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferServiceRespondTest {

    @Mock OfferRepository offerRepository;
    @Mock NotificationApi notificationApi;
    @Mock com.dreamhomes.haven.listing.ListingService listingService;

    OfferService service;

    @BeforeEach
    void setUp() {
        // submit() dependencies (outbox, objectMapper, eventPublisher) are unused here — pass
        // null. notificationApi is exercised by the auto-decline path; listingService is now
        // exercised on accept (auto-close-on-ACCEPT).
        service = new OfferService(offerRepository, listingService, null, notificationApi, null, null);
    }

    @Test
    void ownerCanAcceptPendingOfferWhenNoSiblings() {
        when(offerRepository.findById(50L)).thenReturn(Optional.of(pending(50L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(offerRepository.findByListingIdAndStatusAndIdNot(1L, OfferStatus.PENDING, 50L))
                .thenReturn(List.of());

        Offer result = service.respond(99L, 50L, OfferStatus.ACCEPTED);

        assertThat(result.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    @Test
    void acceptingOfferAutoDeclinesPendingSiblingsAndNotifiesEachLoser() {
        // The acceptance target.
        Offer winner = pending(50L, 99L);
        winner.setApplicantId(100L);
        // Two losing siblings on the same listing, different applicants.
        Offer sibling1 = pending(51L, 99L);
        sibling1.setApplicantId(101L);
        Offer sibling2 = pending(52L, 99L);
        sibling2.setApplicantId(102L);

        when(offerRepository.findById(50L)).thenReturn(Optional.of(winner));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(offerRepository.findByListingIdAndStatusAndIdNot(1L, OfferStatus.PENDING, 50L))
                .thenReturn(List.of(sibling1, sibling2));

        Offer result = service.respond(99L, 50L, OfferStatus.ACCEPTED);

        assertThat(result.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        // Both siblings flipped to DECLINED.
        assertThat(sibling1.getStatus()).isEqualTo(OfferStatus.DECLINED);
        assertThat(sibling2.getStatus()).isEqualTo(OfferStatus.DECLINED);

        // 1 save for winner + 2 saves for siblings = 3 total.
        verify(offerRepository, times(3)).save(any(Offer.class));

        // One OFFER_AUTO_DECLINED notification per losing applicant.
        ArgumentCaptor<Long> recipientCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationApi, times(2))
                .recordSync(eq(NotificationKind.OFFER_AUTO_DECLINED),
                        recipientCaptor.capture(), payloadCaptor.capture());

        assertThat(recipientCaptor.getAllValues()).containsExactly(101L, 102L);
        Map<String, Object> payload = payloadCaptor.getAllValues().get(0);
        assertThat(payload.get("listingId")).isEqualTo(1L);
        assertThat(payload.get("winningOfferId")).isEqualTo(50L);
        assertThat(payload.get("reason")).isEqualTo("ANOTHER_OFFER_ACCEPTED");
    }

    @Test
    void decliningOfferDoesNotTouchSiblings() {
        when(offerRepository.findById(50L)).thenReturn(Optional.of(pending(50L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        Offer result = service.respond(99L, 50L, OfferStatus.DECLINED);

        assertThat(result.getStatus()).isEqualTo(OfferStatus.DECLINED);
        // Sibling lookup never runs on DECLINE — only an ACCEPT triggers fan-out.
        verify(offerRepository, never())
                .findByListingIdAndStatusAndIdNot(anyLong(), any(), anyLong());
        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    @Test
    void rejectsResponseFromNonOwner() {
        when(offerRepository.findById(50L)).thenReturn(Optional.of(pending(50L, 200L)));

        assertThatThrownBy(() -> service.respond(99L, 50L, OfferStatus.ACCEPTED))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(offerRepository, never()).save(any());
    }

    @Test
    void rejectsResponseToAlreadyAcceptedOffer() {
        Offer accepted = pending(50L, 99L);
        accepted.setStatus(OfferStatus.ACCEPTED);
        when(offerRepository.findById(50L)).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service.respond(99L, 50L, OfferStatus.DECLINED))
                .isInstanceOf(InvalidOfferTransitionException.class);

        verify(offerRepository, never()).save(any());
    }

    @Test
    void rejectsResponseToAlreadyDeclinedOffer() {
        Offer declined = pending(50L, 99L);
        declined.setStatus(OfferStatus.DECLINED);
        when(offerRepository.findById(50L)).thenReturn(Optional.of(declined));

        assertThatThrownBy(() -> service.respond(99L, 50L, OfferStatus.ACCEPTED))
                .isInstanceOf(InvalidOfferTransitionException.class);
    }

    @Test
    void rejectsAttemptToTransitionBackToPending() {
        when(offerRepository.findById(50L)).thenReturn(Optional.of(pending(50L, 99L)));

        assertThatThrownBy(() -> service.respond(99L, 50L, OfferStatus.PENDING))
                .isInstanceOf(InvalidOfferTransitionException.class);
    }

    @Test
    void throwsWhenOfferDoesNotExist() {
        when(offerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.respond(99L, 404L, OfferStatus.ACCEPTED))
                .isInstanceOf(OfferNotFoundException.class);
    }

    private static Offer pending(Long offerId, Long ownerId) {
        Instant now = Instant.now().minusSeconds(60);
        return Offer.builder()
                .id(offerId).listingId(1L).applicantId(100L).ownerId(ownerId)
                .amount(new BigDecimal("75000000.00")).currency("NGN")
                .status(OfferStatus.PENDING)
                // Phase 13: original offer was proposed by the applicant. The owner is
                // therefore the one allowed to act on it (not its own proposer).
                .proposedByUserId(100L)
                .createdAt(now).updatedAt(now)
                .build();
    }
}
