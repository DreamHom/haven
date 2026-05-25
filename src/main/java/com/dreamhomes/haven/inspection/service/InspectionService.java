package com.dreamhomes.haven.inspection.service;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.inspection.events.InspectionDecidedEvent;
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
import java.util.List;
import java.util.UUID;
import com.dreamhomes.haven.inspection.dto.RequestInspectionCommand;
import com.dreamhomes.haven.inspection.exception.InspectionRequestInvalidStateException;
import com.dreamhomes.haven.inspection.exception.InspectionRequestNotFoundException;
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

    private static final List<InspectionRequestStatus> ACTIVE_SLOT_STATUSES =
            List.of(InspectionRequestStatus.PENDING, InspectionRequestStatus.APPROVED);

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
     *
     * @deprecated Use {@link #cancelByEitherParty(Long, Long, String)} — the broader
     * path accepts cancellations from owner / assigned agent as well as the applicant,
     * works from both PENDING and APPROVED, and captures a required reason that flows
     * through to the notification of the other party.
     */
    @Deprecated
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

    /**
     * Cancel from PENDING or APPROVED with a required reason. Either party can call —
     * the applicant, the listing owner, or the assigned active agent — and the OTHER
     * parties get a Kafka-fanned notification carrying the reason so they know why.
     *
     * <p>Closes Gap C of post-session-tasks Item 7. Today only PENDING requests can be
     * cancelled via {@link #cancel(Long, Long)} and only by the applicant; once approved
     * both parties are locked in (applicant emergency = forced no-show on their record,
     * owner emergency = ghosted meeting). This path gives both sides a graceful exit.
     *
     * <p>State guard: 409 if the request is in any terminal state (CANCELLED, DECLINED,
     * COMPLETED, NO_SHOW). Reason guard: 400 if the reason is null or blank. Auth guard:
     * 403 if the caller is neither the applicant, the listing owner, nor the assigned
     * active agent.
     *
     * @param callerId  authenticated user attempting the cancellation
     * @param requestId target inspection request id
     * @param reason    required, max 200 chars (column-enforced); trimmed before persist
     */
    @Transactional
    public InspectionRequest cancelByEitherParty(Long callerId, Long requestId, String reason) {
        InspectionRequest request = loadRequest(requestId);
        InspectionRequestStatus current = request.getStatus();
        if (current != InspectionRequestStatus.PENDING && current != InspectionRequestStatus.APPROVED) {
            throw new com.dreamhomes.haven.inspection.exception.InspectionRequestNotCancellableException(requestId);
        }
        String cleanReason = trimToNull(reason);
        if (cleanReason == null) {
            throw new com.dreamhomes.haven.inspection.exception.InspectionCancellationReasonRequiredException();
        }
        Long listingId = requireListingIdForRequest(request);
        Long ownerId = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        Long agentUserId = listingService.activeAgentUserId(listingId);
        boolean authorised = callerId.equals(request.getApplicantId())
                || callerId.equals(ownerId)
                || (agentUserId != null && callerId.equals(agentUserId));
        if (!authorised) {
            throw new com.dreamhomes.haven.listing.exception.NotPropertyOwnerException();
        }

        request.setStatus(InspectionRequestStatus.CANCELLED);
        request.setCancellationReason(cleanReason);
        InspectionRequest saved = requestRepository.save(request);

        publishCancelledOutbox(saved, listingId, ownerId, agentUserId, callerId, cleanReason);
        log.info("Caller {} cancelled inspectionRequestId={} reason={} (was {})",
                callerId, requestId, cleanReason, current);
        return saved;
    }

    private void publishCancelledOutbox(InspectionRequest saved, Long listingId, Long ownerId,
                                        Long agentUserId, Long cancelledByUserId, String reason) {
        UUID eventId = UUID.randomUUID();
        com.dreamhomes.haven.inspection.events.InspectionCancelledEvent event =
                new com.dreamhomes.haven.inspection.events.InspectionCancelledEvent(
                        eventId,
                        saved.getId(),
                        saved.getSlotId(),
                        listingId,
                        saved.getApplicantId(),
                        ownerId,
                        agentUserId,
                        cancelledByUserId,
                        reason,
                        Instant.now());
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("InspectionRequest")
                .aggregateId(saved.getId())
                .eventType(com.dreamhomes.haven.inspection.events.InspectionCancelledEvent.class.getName())
                .topic(com.dreamhomes.haven.inspection.events.InspectionCancelledEvent.TOPIC)
                .partitionKey(String.valueOf(listingId))
                .payload(serialize(event))
                .build());
        applicationEventPublisher.publishEvent(OutboxRowReadyEvent.INSTANCE);
        log.info("Outbox inspection.cancelled.v1 eventId={} inspectionRequestId={} cancelledByUserId={}",
                eventId, saved.getId(), cancelledByUserId);
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

    @Transactional
    public InspectionRequest approveByOwner(Long ownerUserId, Long requestId) {
        return transitionFromPending(ownerUserId, requestId, InspectionRequestStatus.APPROVED, true, null);
    }

    /**
     * Overload kept for backwards compatibility with the original controller call. Callers
     * that want to capture an owner-supplied decline justification should use
     * {@link #declineByOwner(Long, Long, String)}.
     */
    @Transactional
    public InspectionRequest declineByOwner(Long ownerUserId, Long requestId) {
        return declineByOwner(ownerUserId, requestId, null);
    }

    /**
     * Decline with an optional owner-supplied reason. The reason is included on the
     * {@link InspectionDecidedEvent} payload so the applicant-side notification can
     * surface "why" instead of leaving the applicant guessing.
     */
    @Transactional
    public InspectionRequest declineByOwner(Long ownerUserId, Long requestId, String reason) {
        return transitionFromPending(ownerUserId, requestId, InspectionRequestStatus.DECLINED, true,
                trimToNull(reason));
    }

    @Transactional
    public InspectionRequest rescheduleApprovedByAgent(Long agentUserId, Long requestId, Long newSlotId) {
        InspectionRequest request = loadRequest(requestId);
        if (request.getStatus() != InspectionRequestStatus.APPROVED) {
            throw new InspectionRequestInvalidStateException(requestId,
                    "must be APPROVED before it can be rescheduled");
        }
        Long listingId = requireListingIdForRequest(request);
        Long assigned = listingService.activeAgentUserId(listingId);
        if (assigned == null || !assigned.equals(agentUserId)) {
            throw new com.dreamhomes.haven.listing.exception.NotPropertyOwnerException();
        }
        InspectionSlot currentSlot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new SlotNotFoundException(request.getSlotId()));
        InspectionSlot newSlot = slotRepository.findById(newSlotId)
                .orElseThrow(() -> new SlotNotFoundException(newSlotId));
        if (!currentSlot.getListingId().equals(newSlot.getListingId())) {
            throw new com.dreamhomes.haven.inspection.exception.InspectionSlotListingMismatchException();
        }
        if (newSlotId.equals(request.getSlotId())) {
            return request;
        }
        if (requestRepository.existsBySlotIdAndStatusInAndIdNot(newSlotId, ACTIVE_SLOT_STATUSES, requestId)) {
            throw new SlotAlreadyClaimedException();
        }
        request.setSlotId(newSlotId);
        try {
            return requestRepository.save(request);
        } catch (DataIntegrityViolationException ex) {
            throw new SlotAlreadyClaimedException();
        }
    }

    @Transactional
    public InspectionRequest patchAgentExtras(Long agentUserId, Long requestId, String extras) {
        InspectionRequest request = loadRequest(requestId);
        if (request.getStatus() != InspectionRequestStatus.APPROVED) {
            throw new InspectionRequestInvalidStateException(requestId,
                    "must be APPROVED before agent extras can be set");
        }
        Long listingId = requireListingIdForRequest(request);
        Long assigned = listingService.activeAgentUserId(listingId);
        if (assigned == null || !assigned.equals(agentUserId)) {
            throw new com.dreamhomes.haven.listing.exception.NotPropertyOwnerException();
        }
        request.setAgentExtras(trimToNull(extras));
        return requestRepository.save(request);
    }

    @Transactional
    public InspectionRequest markCompletedByAgent(Long agentUserId, Long requestId) {
        InspectionRequest request = loadRequest(requestId);
        if (request.getStatus() != InspectionRequestStatus.APPROVED) {
            throw new InspectionRequestInvalidStateException(requestId,
                    "must be APPROVED before it can be marked completed");
        }
        Long listingId = requireListingIdForRequest(request);
        Long assigned = listingService.activeAgentUserId(listingId);
        if (assigned == null || !assigned.equals(agentUserId)) {
            throw new com.dreamhomes.haven.listing.exception.NotPropertyOwnerException();
        }
        request.setStatus(InspectionRequestStatus.COMPLETED);
        return requestRepository.save(request);
    }

    @Transactional
    public InspectionRequest markNoShow(Long callerId, Long requestId) {
        InspectionRequest request = loadRequest(requestId);
        if (request.getStatus() != InspectionRequestStatus.APPROVED) {
            throw new InspectionRequestInvalidStateException(requestId,
                    "must be APPROVED before it can be marked as no-show");
        }
        Long listingId = requireListingIdForRequest(request);
        Long ownerId = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        Long assigned = listingService.activeAgentUserId(listingId);
        boolean ok = callerId.equals(ownerId)
                || (assigned != null && callerId.equals(assigned));
        if (!ok) {
            throw new com.dreamhomes.haven.listing.exception.NotPropertyOwnerException();
        }
        request.setStatus(InspectionRequestStatus.NO_SHOW);
        return requestRepository.save(request);
    }

    private InspectionRequest transitionFromPending(Long ownerUserId, Long requestId,
                                                    InspectionRequestStatus target, boolean requireOwner,
                                                    String reason) {
        InspectionRequest request = loadRequest(requestId);
        if (request.getStatus() != InspectionRequestStatus.PENDING) {
            throw new InspectionRequestInvalidStateException(requestId,
                    "must be PENDING for owner decision");
        }
        Long listingId = requireListingIdForRequest(request);
        Long ownerId = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (requireOwner && !ownerUserId.equals(ownerId)) {
            throw new com.dreamhomes.haven.listing.exception.NotPropertyOwnerException();
        }
        request.setStatus(target);
        InspectionRequest saved = requestRepository.save(request);

        // Outbox the decision so the async fan-out (applicant notification) can run
        // outside this transaction without coupling the owner's decision to the
        // applicant's in-tray availability.
        publishDecisionOutbox(saved, listingId, target, reason);
        return saved;
    }

    private void publishDecisionOutbox(InspectionRequest saved, Long listingId,
                                       InspectionRequestStatus target, String reason) {
        UUID eventId = UUID.randomUUID();
        InspectionDecidedEvent.Decision decision = (target == InspectionRequestStatus.APPROVED)
                ? InspectionDecidedEvent.Decision.APPROVED
                : InspectionDecidedEvent.Decision.DECLINED;
        InspectionDecidedEvent event = new InspectionDecidedEvent(
                eventId,
                saved.getId(),
                saved.getSlotId(),
                listingId,
                saved.getApplicantId(),
                decision,
                reason,
                Instant.now());
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("InspectionRequest")
                .aggregateId(saved.getId())
                .eventType(InspectionDecidedEvent.class.getName())
                .topic(InspectionDecidedEvent.TOPIC)
                // Same per-listing ordering as the requested event so consumers see
                // requested → decided in the same partition order.
                .partitionKey(String.valueOf(listingId))
                .payload(serialize(event))
                .build());
        applicationEventPublisher.publishEvent(OutboxRowReadyEvent.INSTANCE);
        log.info("Outbox inspection.decided.v1 eventId={} inspectionRequestId={} decision={}",
                eventId, saved.getId(), decision);
    }

    private InspectionRequest loadRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new InspectionRequestNotFoundException(requestId));
    }

    private Long requireListingIdForRequest(InspectionRequest request) {
        InspectionSlot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new SlotNotFoundException(request.getSlotId()));
        return slot.getListingId();
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }
}
