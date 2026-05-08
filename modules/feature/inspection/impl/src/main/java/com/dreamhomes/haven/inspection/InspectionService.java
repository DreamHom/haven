package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.ListingApi;
import com.dreamhomes.haven.listing.ListingResponse;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionSlotRepository slotRepository;
    private final InspectionRequestRepository requestRepository;
    private final ListingApi listingApi;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public InspectionRequest requestSlot(Long applicantId, RequestInspectionCommand cmd) {
        InspectionSlot slot = slotRepository.findById(cmd.slotId())
                .orElseThrow(() -> new SlotNotFoundException(cmd.slotId()));
        // Throws ListingNotFoundException if missing — propagates as 404 RFC 7807.
        ListingResponse listing = listingApi.findById(slot.getListingId());

        Instant now = Instant.now();
        InspectionRequest request = InspectionRequest.builder()
                .slotId(slot.getId())
                .applicantId(applicantId)
                .status(InspectionRequestStatus.PENDING)
                .notes(cmd.notes())
                .createdAt(now)
                .updatedAt(now)
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
        InspectionRequestedEvent event = new InspectionRequestedEvent(
                eventId,
                saved.getId(),
                saved.getSlotId(),
                listing.id(),
                listing.ownerId(),
                applicantId,
                slot.getStartsAt(),
                slot.getEndsAt(),
                now);
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("InspectionRequest")
                .aggregateId(saved.getId())
                .eventType(InspectionRequestedEvent.class.getName())
                .topic(InspectionRequestedEvent.TOPIC)
                // Per-listing ordering — system-architecture diagram says "key = listingId".
                .partitionKey(String.valueOf(listing.id()))
                .payload(serialize(event))
                .createdAt(now)
                .build());

        // Nudge the relay to drain right after this transaction commits, instead of
        // waiting for the next scheduled poll. The scheduled poll is still the safety
        // net for crashes between commit and listener invocation.
        applicationEventPublisher.publishEvent(OutboxRowReadyEvent.INSTANCE);

        log.info("Created inspectionRequestId={} slotId={} applicantId={} eventId={}",
                saved.getId(), saved.getSlotId(), applicantId, eventId);
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
