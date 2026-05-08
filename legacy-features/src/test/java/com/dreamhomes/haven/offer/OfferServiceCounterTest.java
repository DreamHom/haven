package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.NotificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * Phase 13 — counter-offer chain. Submit + respond logic lives in their own test files;
 * this one is focused on the counter() flow specifically.
 */
@ExtendWith(MockitoExtension.class)
class OfferServiceCounterTest {

    @Mock OfferRepository offerRepository;
    @Mock NotificationApi notificationApi;

    OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerRepository, null, null, notificationApi,
                new com.fasterxml.jackson.databind.ObjectMapper(), null);
    }

    @Test
    void ownerCountersApplicantsPendingOffer_parentMarkedCounteredChildPersistedApplicantNotified() {
        Offer parent = pending(/*offerId=*/50L, /*ownerId=*/99L, /*applicantId=*/100L,
                /*proposedBy=*/100L);
        when(offerRepository.findById(50L)).thenReturn(Optional.of(parent));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            if (o.getId() == null) o.setId(123L);
            return o;
        });

        Offer child = service.counter(/*callerId=*/99L, 50L, new BigDecimal("90000000.00"), "How about this?");

        // Two saves: parent COUNTERED + child PENDING.
        ArgumentCaptor<Offer> cap = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository, times(2)).save(cap.capture());
        Offer savedParent = cap.getAllValues().get(0);
        Offer savedChild = cap.getAllValues().get(1);

        assertThat(savedParent.getStatus()).isEqualTo(OfferStatus.COUNTERED);
        assertThat(savedChild.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(savedChild.getParentOfferId()).isEqualTo(50L);
        assertThat(savedChild.getProposedByUserId()).isEqualTo(99L); // owner
        assertThat(savedChild.getAmount()).isEqualByComparingTo("90000000.00");
        assertThat(savedChild.getListingId()).isEqualTo(parent.getListingId());

        // Sync notification to applicant (the OTHER party).
        verify(notificationApi).recordSync(eq(NotificationKind.OFFER_COUNTERED), eq(100L), any());
    }

    @Test
    void applicantCountersOwnersPendingCounter_chainAlternatesProposer() {
        // The owner had countered earlier; the applicant counters back.
        Offer ownersCounter = pending(/*offerId=*/123L, /*ownerId=*/99L, /*applicantId=*/100L,
                /*proposedBy=*/99L);
        when(offerRepository.findById(123L)).thenReturn(Optional.of(ownersCounter));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            if (o.getId() == null) o.setId(456L);
            return o;
        });

        service.counter(/*callerId=*/100L, 123L, new BigDecimal("85000000.00"), null);

        ArgumentCaptor<Offer> cap = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository, times(2)).save(cap.capture());
        Offer savedChild = cap.getAllValues().get(1);
        assertThat(savedChild.getProposedByUserId()).isEqualTo(100L); // applicant

        verify(notificationApi).recordSync(eq(NotificationKind.OFFER_COUNTERED), eq(99L), any()); // owner
    }

    @Test
    void cannotCounterYourOwnPendingOffer() {
        Offer parent = pending(50L, 99L, 100L, /*proposedBy=*/100L);
        when(offerRepository.findById(50L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.counter(/*callerId=*/100L, 50L, new BigDecimal("1"), null))
                .isInstanceOf(CannotActOnOwnOfferException.class);

        verify(offerRepository, never()).save(any());
        verify(notificationApi, never()).recordSync(any(), anyLong(), any());
    }

    @Test
    void nonParticipantCannotCounter() {
        Offer parent = pending(50L, 99L, 100L, /*proposedBy=*/100L);
        when(offerRepository.findById(50L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.counter(/*callerId=*/200L, 50L, new BigDecimal("1"), null))
                .isInstanceOf(NotPropertyOwnerException.class);
    }

    @Test
    void cannotCounterATerminalOffer() {
        Offer terminal = pending(50L, 99L, 100L, 100L);
        terminal.setStatus(OfferStatus.ACCEPTED);
        when(offerRepository.findById(50L)).thenReturn(Optional.of(terminal));

        assertThatThrownBy(() -> service.counter(99L, 50L, new BigDecimal("1"), null))
                .isInstanceOf(InvalidOfferTransitionException.class);
    }

    @Test
    void counterRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.counter(99L, 50L, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.counter(99L, 50L, new BigDecimal("-100"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void counterOnNonExistentOfferIs404() {
        when(offerRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.counter(99L, 404L, new BigDecimal("1"), null))
                .isInstanceOf(OfferNotFoundException.class);
    }

    @Test
    void respondByProposerIsRejected() {
        // Direct test for the new constraint on respond(): the PROPOSER can't accept
        // their own offer. (Old respond() only allowed owner; now any participant can,
        // but not the proposer.)
        Offer parent = pending(50L, 99L, 100L, /*proposedBy=*/100L);
        when(offerRepository.findById(50L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.respond(/*callerId=*/100L, 50L, OfferStatus.ACCEPTED))
                .isInstanceOf(CannotActOnOwnOfferException.class);
    }

    private static Offer pending(Long offerId, Long ownerId, Long applicantId, Long proposedById) {
        Instant now = Instant.now();
        return Offer.builder()
                .id(offerId).listingId(7L)
                .applicantId(applicantId).ownerId(ownerId)
                .proposedByUserId(proposedById)
                .amount(new BigDecimal("75000000.00")).currency("NGN")
                .status(OfferStatus.PENDING)
                .createdAt(now).updatedAt(now).version(0L)
                .build();
    }
}
