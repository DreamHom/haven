package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
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
import com.dreamhomes.haven.inspection.exception.InspectionRequestInvalidStateException;
import com.dreamhomes.haven.inspection.exception.InspectionSlotListingMismatchException;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.inspection.dto.RequestInspectionCommand;
import com.dreamhomes.haven.inspection.exception.SlotAlreadyClaimedException;
import com.dreamhomes.haven.inspection.exception.SlotNotFoundException;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.inspection.service.InspectionService;
import com.dreamhomes.haven.inspection.repository.InspectionRequestRepository;
import com.dreamhomes.haven.inspection.repository.InspectionSlotRepository;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock InspectionSlotRepository slotRepository;
    @Mock InspectionRequestRepository requestRepository;
    @Mock ListingService listingService;
    @Mock OutboxEventRepository outboxRepository;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock com.dreamhomes.haven.notification.NotificationApi notificationApi;

    InspectionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionService(slotRepository, requestRepository, listingService,
                outboxRepository, new ObjectMapper().findAndRegisterModules(),
                applicationEventPublisher, notificationApi);
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

    @Test
    void agentRescheduleMovesApprovedRequestToNewSlotOnSameListing() {
        InspectionRequest req = approvedRequest(10L, 1L);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slotFor(1L, 7L)));
        when(slotRepository.findById(60L)).thenReturn(Optional.of(slotFor(60L, 7L)));
        when(listingService.activeAgentUserId(7L)).thenReturn(50L);
        when(requestRepository.existsBySlotIdAndStatusInAndIdNot(org.mockito.ArgumentMatchers.eq(60L),
                any(), org.mockito.ArgumentMatchers.eq(10L))).thenReturn(false);
        when(requestRepository.save(any(InspectionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectionRequest updated = service.rescheduleApprovedByAgent(50L, 10L, 60L);

        assertThat(updated.getSlotId()).isEqualTo(60L);
        ArgumentCaptor<InspectionRequest> cap = ArgumentCaptor.forClass(InspectionRequest.class);
        verify(requestRepository).save(cap.capture());
        assertThat(cap.getValue().getSlotId()).isEqualTo(60L);
    }

    @Test
    void agentRescheduleToSameSlotDoesNotPersist() {
        InspectionRequest req = approvedRequest(10L, 1L);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slotFor(1L, 7L)));
        when(listingService.activeAgentUserId(7L)).thenReturn(50L);

        InspectionRequest out = service.rescheduleApprovedByAgent(50L, 10L, 1L);

        assertThat(out.getSlotId()).isEqualTo(1L);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void agentRescheduleRejectsSlotOnDifferentListing() {
        InspectionRequest req = approvedRequest(10L, 1L);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slotFor(1L, 7L)));
        when(slotRepository.findById(60L)).thenReturn(Optional.of(slotFor(60L, 99L)));
        when(listingService.activeAgentUserId(7L)).thenReturn(50L);

        assertThatThrownBy(() -> service.rescheduleApprovedByAgent(50L, 10L, 60L))
                .isInstanceOf(InspectionSlotListingMismatchException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void agentRescheduleRequiresApprovedState() {
        InspectionRequest req = InspectionRequest.builder()
                .id(10L).slotId(1L).applicantId(2L)
                .status(InspectionRequestStatus.PENDING)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));

        assertThatThrownBy(() -> service.rescheduleApprovedByAgent(50L, 10L, 60L))
                .isInstanceOf(InspectionRequestInvalidStateException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void agentRescheduleRequiresAssignedAgent() {
        InspectionRequest req = approvedRequest(10L, 1L);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slotFor(1L, 7L)));
        when(listingService.activeAgentUserId(7L)).thenReturn(99L);

        assertThatThrownBy(() -> service.rescheduleApprovedByAgent(50L, 10L, 60L))
                .isInstanceOf(NotPropertyOwnerException.class);
    }

    @Test
    void agentRescheduleRejectsWhenTargetSlotAlreadyActive() {
        InspectionRequest req = approvedRequest(10L, 1L);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slotFor(1L, 7L)));
        when(slotRepository.findById(60L)).thenReturn(Optional.of(slotFor(60L, 7L)));
        when(listingService.activeAgentUserId(7L)).thenReturn(50L);
        when(requestRepository.existsBySlotIdAndStatusInAndIdNot(org.mockito.ArgumentMatchers.eq(60L),
                any(), org.mockito.ArgumentMatchers.eq(10L))).thenReturn(true);

        assertThatThrownBy(() -> service.rescheduleApprovedByAgent(50L, 10L, 60L))
                .isInstanceOf(SlotAlreadyClaimedException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void patchAgentExtrasTrimsWhitespace() {
        InspectionRequest req = approvedRequest(10L, 1L);
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slotFor(1L, 7L)));
        when(listingService.activeAgentUserId(7L)).thenReturn(50L);
        when(requestRepository.save(any(InspectionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectionRequest saved = service.patchAgentExtras(50L, 10L, "  Park on B1  ");

        assertThat(saved.getAgentExtras()).isEqualTo("Park on B1");
    }

    @Test
    void patchAgentExtrasBlankClearsField() {
        InspectionRequest req = approvedRequest(10L, 1L);
        req.setAgentExtras("old");
        when(requestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(slotRepository.findById(1L)).thenReturn(Optional.of(slotFor(1L, 7L)));
        when(listingService.activeAgentUserId(7L)).thenReturn(50L);
        when(requestRepository.save(any(InspectionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        InspectionRequest saved = service.patchAgentExtras(50L, 10L, "   ");

        assertThat(saved.getAgentExtras()).isNull();
    }

    private static InspectionRequest approvedRequest(long id, long slotId) {
        return InspectionRequest.builder()
                .id(id).slotId(slotId).applicantId(2L)
                .status(InspectionRequestStatus.APPROVED)
                .notes("n")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
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
                null, null, null, null,
                null, false,
                ListingStatus.LIVE, null, 0L, now, now, null, null, null, null, null, null, null);
    }
}
