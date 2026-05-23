package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.dreamhomes.haven.offer.dto.SubmitOfferCommand;
import com.dreamhomes.haven.offer.exception.ListingNotOpenForOffersException;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.model.OfferStatus;

@ExtendWith(MockitoExtension.class)
class OfferServiceSubmitTest {

    @Mock OfferRepository offerRepository;
    @Mock ListingService listingService;
    @Mock com.dreamhomes.haven.agentlisting.AgentListingRepository agentListingRepository;
    @Mock OutboxEventRepository outboxRepository;
    @Mock com.dreamhomes.haven.notification.NotificationApi notificationApi;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerRepository, listingService, agentListingRepository,
                outboxRepository, notificationApi,
                new ObjectMapper().findAndRegisterModules(),
                applicationEventPublisher);
    }

    @Test
    void persistsPendingOfferAndWritesOutboxRowInSameTransaction() throws Exception {
        when(listingService.findById(7L)).thenReturn(liveListing(7L, 99L));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            o.setId(123L);
            return o;
        });

        service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("75000000.00"), "NGN", "I love it", null));

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
        when(listingService.findById(7L)).thenReturn(liveListing(7L, 99L));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            o.setId(123L);
            return o;
        });

        service.submit(100L, new SubmitOfferCommand(7L, new BigDecimal("100"), "NGN", null, null));

        verify(applicationEventPublisher).publishEvent(OutboxRowReadyEvent.INSTANCE);
    }

    @Test
    void doesNotFireOutboxRowReadyEventWhenSubmitFails() {
        when(listingService.findById(7L)).thenReturn(listing(7L, 99L, ListingStatus.PAUSED));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null, null)))
                .isInstanceOf(ListingNotOpenForOffersException.class);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void defaultsCurrencyToNgnWhenCallerOmitsIt() {
        when(listingService.findById(7L)).thenReturn(liveListing(7L, 99L));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
            Offer o = inv.getArgument(0);
            o.setId(42L);
            return o;
        });

        Offer result = service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null, null));

        assertThat(result.getCurrency()).isEqualTo("NGN");
    }

    @Test
    void throwsWhenListingDoesNotExistAndDoesNotWriteOutbox() {
        when(listingService.findById(404L)).thenThrow(new ListingNotFoundException(404L));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                404L, new BigDecimal("100"), null, null, null)))
                .isInstanceOf(ListingNotFoundException.class);

        verify(offerRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void rejectsOfferOnPausedListingAndDoesNotWriteOutbox() {
        when(listingService.findById(7L)).thenReturn(listing(7L, 99L, ListingStatus.PAUSED));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null, null)))
                .isInstanceOf(ListingNotOpenForOffersException.class);

        verify(offerRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void rejectsOfferOnClosedListing() {
        when(listingService.findById(7L)).thenReturn(listing(7L, 99L, ListingStatus.CLOSED));

        assertThatThrownBy(() -> service.submit(100L, new SubmitOfferCommand(
                7L, new BigDecimal("100"), null, null, null)))
                .isInstanceOf(ListingNotOpenForOffersException.class);
    }

    private static ListingResponse liveListing(Long listingId, Long ownerId) {
        return listing(listingId, ownerId, ListingStatus.LIVE);
    }

    private static ListingResponse listing(Long listingId, Long ownerId, ListingStatus status) {
        Instant now = Instant.now();
        return new ListingResponse(listingId, 1L, ownerId, ListingType.SALE,
                new BigDecimal("80000000.00"), "NGN", null, null, null,
                null, null, null, null,
                null, false,
                status, null, 0L, now, now, null, null, null, null, null, null, null);
    }
}
