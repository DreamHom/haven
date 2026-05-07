package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferServiceSubmitTest {

    @Mock OfferRepository offerRepository;
    @Mock ListingRepository listingRepository;
    @Mock OfferEventPublisher eventPublisher;

    OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerRepository, listingRepository, eventPublisher);
    }

    @Test
    void persistsPendingOfferAndPublishesEvent() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            o.setId(123L);
            return o;
        });

        Offer result = service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("75000000.00"), "NGN", "I love it"));

        ArgumentCaptor<Offer> captor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(captor.capture());
        Offer saved = captor.getValue();
        assertThat(saved.getListingId()).isEqualTo(7L);
        assertThat(saved.getApplicantId()).isEqualTo(100L);
        assertThat(saved.getOwnerId()).isEqualTo(99L);
        assertThat(saved.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(saved.getCurrency()).isEqualTo("NGN");

        ArgumentCaptor<OfferSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(OfferSubmittedEvent.class);
        verify(eventPublisher).publishOfferSubmitted(eventCaptor.capture());
        OfferSubmittedEvent event = eventCaptor.getValue();
        assertThat(event.offerId()).isEqualTo(123L);
        assertThat(event.listingId()).isEqualTo(7L);
        assertThat(event.ownerId()).isEqualTo(99L);
        assertThat(event.applicantId()).isEqualTo(100L);
        assertThat(event.amount()).isEqualByComparingTo("75000000.00");

        assertThat(result.getId()).isEqualTo(123L);
    }

    @Test
    void defaultsCurrencyToNgnWhenCallerOmitsIt() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        Offer result = service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null));

        assertThat(result.getCurrency()).isEqualTo("NGN");
    }

    @Test
    void throwsWhenListingDoesNotExistAndDoesNotPublish() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                404L, new BigDecimal("100"), null, null)))
                .isInstanceOf(ListingNotFoundException.class);

        verify(offerRepository, never()).save(any());
        verify(eventPublisher, never()).publishOfferSubmitted(any());
    }

    @Test
    void rejectsOfferOnPausedListingAndDoesNotPublish() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(
                listing(7L, 99L, ListingStatus.PAUSED)));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null)))
                .isInstanceOf(ListingNotOpenForOffersException.class);

        verify(offerRepository, never()).save(any());
        verify(eventPublisher, never()).publishOfferSubmitted(any());
    }

    @Test
    void rejectsOfferOnClosedListingAndDoesNotPublish() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(
                listing(7L, 99L, ListingStatus.CLOSED)));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null)))
                .isInstanceOf(ListingNotOpenForOffersException.class);
    }

    private static Listing liveListing(Long listingId, Long ownerId) {
        return listing(listingId, ownerId, ListingStatus.LIVE);
    }

    private static Listing listing(Long listingId, Long ownerId, ListingStatus status) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(listingId).propertyId(1L).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(status)
                .createdAt(now).updatedAt(now).build();
    }
}
