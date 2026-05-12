package com.dreamhomes.haven.inspection.service;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import com.dreamhomes.haven.inspection.dto.RequestInspectionCommand;
import com.dreamhomes.haven.inspection.exception.SlotAlreadyClaimedException;
import com.dreamhomes.haven.inspection.exception.SlotNotFoundException;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.inspection.repository.InspectionRequestRepository;
import com.dreamhomes.haven.inspection.repository.InspectionSlotRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionSlotRepository slotRepository;
    private final InspectionRequestRepository requestRepository;
    private final ListingService listingService;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final com.dreamhomes.haven.notification.NotificationApi notificationApi;

    /**
     * Applicant's bookings. Backs {@code GET /api/inspections/mine} — the read-side
     * Temi flagged as missing in the persona audit ("I booked a slot and have no
     * way to see my upcoming inspections").
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<InspectionRequest> listMine(
            Long applicantId, org.springframework.data.domain.Pageable pageable) {
        return requestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId, pageable);
    }

    /**
     * Applicant withdraws a PENDING inspection request. Frees the slot for others.
     * Persona audit (Temi): the previous shape locked the applicant in once they'd
     * claimed a slot, with no recourse if something came up at work.
     */
    @Transactional
    public InspectionRequest cancel(Long callerId, Long requestId) {
        InspectionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new com.dreamhomes.haven.inspection.exception.InspectionRequestNotFoundException(requestId));
        if (!request.getApplicantId().equals(callerId)) {
            throw new com.dreamhomes.haven.listing.exception.NotPropertyOwnerException();
        }
        if (request.getStatus() != InspectionRequestStatus.PENDING) {
            throw new com.dreamhomes.haven.inspection.exception.InspectionRequestNotPendingException(requestId);
        }
        request.setStatus(InspectionRequestStatus.CANCELLED);
        InspectionRequest saved = requestRepository.save(request);
        log.info("Applicant {} cancelled inspection request {} (slot {} freed)",
                callerId, requestId, saved.getSlotId());
        return saved;
    }

    @Transactional
    public InspectionRequest requestSlot(Long applicantId, RequestInspectionCommand cmd) {
        InspectionSlot slot = slotRepository.findById(cmd.slotId())
                .orElseThrow(() -> new SlotNotFoundException(cmd.slotId()));
        // Throws ListingNotFoundException if missing — propagates as 404 RFC 7807.
        ListingResponse listing = listingService.findById(slot.getListingId());

        InspectionRequest request = InspectionRequest.builder()
                .slotId(slot.getId())
                .applicantId(applicantId)
                .status(InspectionRequestStatus.PENDING)
                .notes(cmd.notes())
                .build();

        InspectionRequest saved;
        try {
            saved = requestRepository.save(request);
        } catch (DataIntegrityViolationException raceWithAnotherApplicant) {
            // The partial unique index already blocked the duplicate active claim.
            // Translate to a 409 so the API speaks the same shape every other duplicate
            // path uses.
            throw new SlotAlreadyClaimedException();
        }

        // Outbox write is part of the same JPA transaction as the inspection_request
        // insert above — both commit together or neither does. The OutboxRelay ships
        // it to Kafka asynchronously after this returns.
        UUID eventId = UUID.randomUUID();
        // Domain timestamp for the event's occurredAt — separate from the row's audit createdAt.
        Instant occurredAt = Instant.now();
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                eventId,
                saved.getId(),
                saved.getSlotId(),
                listing.id(),
                listing.ownerId(),
                applicantId,
                slot.getStartsAt(),
                slot.getEndsAt(),
                occurredAt);
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("InspectionRequest")
                .aggregateId(saved.getId())
                .eventType(InspectionRequestedEvent.class.getName())
                .topic(InspectionRequestedEvent.TOPIC)
                // Per-listing ordering — system-architecture diagram says "key = listingId".
                .partitionKey(String.valueOf(listing.id()))
                .payload(serialize(event))
                .build());

        // Nudge the relay to drain right after this transaction commits, instead of
        // waiting for the next scheduled poll. The scheduled poll is still the safety
        // net for crashes between commit and listener invocation.
        applicationEventPublisher.publishEvent(OutboxRowReadyEvent.INSTANCE);

        log.info("Created inspectionRequestId={} slotId={} applicantId={} eventId={}",
                saved.getId(), saved.getSlotId(), applicantId, eventId);
        // Persona audit (Ngozi, Temi): the applicant who just booked deserves an immediate
        // in-tray ack independent of the async Kafka fanout to owner + agent.
        notificationApi.recordSync(
                com.dreamhomes.haven.notification.model.NotificationKind.INSPECTION_BOOKED,
                applicantId,
                java.util.Map.of(
                        "inspectionRequestId", saved.getId(),
                        "slotId", saved.getSlotId(),
                        "listingId", listing.id(),
                        "startsAt", slot.getStartsAt().toString()));
        return saved;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }
}
