package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingResponse;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

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
class InspectionServiceTest {

    @Mock InspectionSlotRepository slotRepository;
    @Mock InspectionRequestRepository requestRepository;
    @Mock ListingService listingService;
    @Mock OutboxEventRepository outboxRepository;
    @Mock ApplicationEventPublisher applicationEventPublisher;

    InspectionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionService(slotRepository, requestRepository, listingService,
                outboxRepository, new ObjectMapper().findAndRegisterModules(),
                applicationEventPublisher);
    }

    @Test
    void persistsPendingRequestAndWritesOutboxRowInSameTransaction() throws Exception {
        Long applicantId = 100L;
        InspectionSlot slot = slotFor(50L, 7L);
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slot));
        when(listingService.findById(7L)).thenReturn(listingResponse(7L, 99L));
        when(requestRepository.save(any(InspectionRequest.class))).thenAnswer(inv -> {
            InspectionRequest r = inv.getArgument(0);
            r.setId(1234L);
            return r;
        });

        service.requestSlot(applicantId, new RequestInspectionCommand(50L, "I'm interested"));

        ArgumentCaptor<OutboxEvent> outboxCap = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCap.capture());
        OutboxEvent saved = outboxCap.getValue();
        assertThat(saved.getEventId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("InspectionRequest");
        assertThat(saved.getAggregateId()).isEqualTo(1234L);
        assertThat(saved.getEventType()).isEqualTo(InspectionRequestedEvent.class.getName());
        assertThat(saved.getTopic()).isEqualTo(InspectionRequestedEvent.TOPIC);
        assertThat(saved.getPartitionKey()).isEqualTo("7");  // listingId — per-listing ordering
        assertThat(saved.getPublishedAt()).isNull();

        InspectionRequestedEvent payload = new ObjectMapper().findAndRegisterModules()
                .readValue(saved.getPayload(), InspectionRequestedEvent.class);
        assertThat(payload.eventId()).isEqualTo(saved.getEventId());
        assertThat(payload.inspectionRequestId()).isEqualTo(1234L);
        assertThat(payload.listingId()).isEqualTo(7L);
        assertThat(payload.ownerId()).isEqualTo(99L);
        assertThat(payload.applicantId()).isEqualTo(applicantId);
    }

    @Test
    void firesOutboxRowReadyEventSoTheRelayCanShipImmediatelyOnCommit() {
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slotFor(50L, 7L)));
        when(listingService.findById(7L)).thenReturn(listingResponse(7L, 99L));
        when(requestRepository.save(any(InspectionRequest.class))).thenAnswer(inv -> {
            InspectionRequest r = inv.getArgument(0);
            r.setId(1234L);
            return r;
        });

        service.requestSlot(100L, new RequestInspectionCommand(50L, null));

        verify(applicationEventPublisher).publishEvent(OutboxRowReadyEvent.INSTANCE);
    }

    @Test
    void doesNotFireOutboxRowReadyEventWhenTheRequestFailsToPersist() {
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slotFor(50L, 7L)));
        when(listingService.findById(7L)).thenReturn(listingResponse(7L, 99L));
        when(requestRepository.save(any(InspectionRequest.class)))
                .thenThrow(new DataIntegrityViolationException("dup slot"));

        assertThatThrownBy(() -> service.requestSlot(100L, new RequestInspectionCommand(50L, null)))
                .isInstanceOf(SlotAlreadyClaimedException.class);

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void throwsWhenSlotDoesNotExistAndDoesNotWriteOutbox() {
        when(slotRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestSlot(100L, new RequestInspectionCommand(404L, null)))
                .isInstanceOf(SlotNotFoundException.class);

        verify(requestRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void translatesPartialUniqueViolationToSlotAlreadyClaimedAndDoesNotWriteOutbox() {
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slotFor(50L, 7L)));
        when(listingService.findById(7L)).thenReturn(listingResponse(7L, 99L));
        when(requestRepository.save(any(InspectionRequest.class)))
                .thenThrow(new DataIntegrityViolationException("dup slot"));

        assertThatThrownBy(() -> service.requestSlot(100L, new RequestInspectionCommand(50L, null)))
                .isInstanceOf(SlotAlreadyClaimedException.class);

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void throwsWhenSlotPointsAtAVanishedListing() {
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slotFor(50L, 7L)));
        when(listingService.findById(7L)).thenThrow(new ListingNotFoundException(7L));

        assertThatThrownBy(() -> service.requestSlot(100L, new RequestInspectionCommand(50L, null)))
                .isInstanceOf(ListingNotFoundException.class);
    }

    private static InspectionSlot slotFor(Long slotId, Long listingId) {
        return InspectionSlot.builder()
                .id(slotId).listingId(listingId)
                .startsAt(Instant.parse("2026-06-01T10:00:00Z"))
                .endsAt(Instant.parse("2026-06-01T11:00:00Z"))
                .createdAt(Instant.now()).build();
    }

    private static ListingResponse listingResponse(Long listingId, Long ownerId) {
        Instant now = Instant.now();
        return new ListingResponse(listingId, 1L, ownerId, ListingType.RENT,
                new BigDecimal("100.00"), "NGN", null, null, null,
                ListingStatus.LIVE, null, 0L, now, now, null);
    }
}
