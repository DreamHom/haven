package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferServiceRespondTest {

    @Mock OfferRepository offerRepository;

    OfferService service;

    @BeforeEach
    void setUp() {
        // submit() dependencies (listingRepository, outbox, notification repo,
        // objectMapper, eventPublisher) are unused here — pass nulls; respond() never
        // touches them.
        service = new OfferService(offerRepository, null, null, null, null, null);
    }

    @Test
    void ownerCanAcceptPendingOffer() {
        when(offerRepository.findById(50L)).thenReturn(Optional.of(pending(50L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        Offer result = service.respond(99L, 50L, OfferStatus.ACCEPTED);

        assertThat(result.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
    }

    @Test
    void ownerCanDeclinePendingOffer() {
        when(offerRepository.findById(50L)).thenReturn(Optional.of(pending(50L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        Offer result = service.respond(99L, 50L, OfferStatus.DECLINED);

        assertThat(result.getStatus()).isEqualTo(OfferStatus.DECLINED);
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
