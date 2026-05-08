package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock OutboxEventRepository outboxRepository;
    @Mock com.dreamhomes.haven.notification.NotificationApi notificationApi;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerRepository, listingRepository,
                outboxRepository, notificationApi,
                new ObjectMapper().findAndRegisterModules(),
                applicationEventPublisher);
    }

    @Test
    void persistsPendingOfferAndWritesOutboxRowInSameTransaction() throws Exception {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            o.setId(123L);
            return o;
        });

        service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("75000000.00"), "NGN", "I love it"));

        ArgumentCaptor<Offer> offerCap = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCap.capture());
        assertThat(offerCap.getValue().getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(offerCap.getValue().getOwnerId()).isEqualTo(99L);

        ArgumentCaptor<OutboxEvent> outboxCap = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCap.capture());
        OutboxEvent saved = outboxCap.getValue();
        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("Offer");
        assertThat(saved.getAggregateId()).isEqualTo(123L);
        assertThat(saved.getEventType()).isEqualTo(OfferSubmittedEvent.class.getName());
        assertThat(saved.getTopic()).isEqualTo(OfferSubmittedEvent.TOPIC);
        assertThat(saved.getPartitionKey()).isEqualTo("7");  // listingId
        assertThat(saved.getPublishedAt()).isNull();

        OfferSubmittedEvent payload = new ObjectMapper().findAndRegisterModules()
                .readValue(saved.getPayload(), OfferSubmittedEvent.class);
        assertThat(payload.eventId()).isEqualTo(saved.getEventId());
        assertThat(payload.offerId()).isEqualTo(123L);
        assertThat(payload.listingId()).isEqualTo(7L);
        assertThat(payload.ownerId()).isEqualTo(99L);
        assertThat(payload.applicantId()).isEqualTo(100L);
        assertThat(payload.amount()).isEqualByComparingTo("75000000.00");
    }

    @Test
    void firesOutboxRowReadyEventSoTheRelayCanShipImmediatelyOnCommit() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(liveListing(7L, 99L)));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            o.setId(123L);
            return o;
        });

        service.submit(100L, new SubmitOfferCommand(7L, new BigDecimal("100"), "NGN", null));

        verify(applicationEventPublisher).publishEvent(OutboxRowReadyEvent.INSTANCE);
    }

    @Test
    void doesNotFireOutboxRowReadyEventWhenSubmitFails() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(
                listing(7L, 99L, ListingStatus.PAUSED)));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null)))
                .isInstanceOf(ListingNotOpenForOffersException.class);

        verify(applicationEventPublisher, never()).publishEvent(any());
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
    void throwsWhenListingDoesNotExistAndDoesNotWriteOutbox() {
        when(listingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                404L, new BigDecimal("100"), null, null)))
                .isInstanceOf(ListingNotFoundException.class);

        verify(offerRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void rejectsOfferOnPausedListingAndDoesNotWriteOutbox() {
        when(listingRepository.findById(7L)).thenReturn(Optional.of(
                listing(7L, 99L, ListingStatus.PAUSED)));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null)))
                .isInstanceOf(ListingNotOpenForOffersException.class);

        verify(offerRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void rejectsOfferOnClosedListing() {
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
