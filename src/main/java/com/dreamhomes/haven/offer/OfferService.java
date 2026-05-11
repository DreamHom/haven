package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.offer.dto.SubmitOfferCommand;
import com.dreamhomes.haven.offer.exception.CannotActOnOwnOfferException;
import com.dreamhomes.haven.offer.exception.InvalidOfferTransitionException;
import com.dreamhomes.haven.offer.exception.ListingNotOpenForOffersException;
import com.dreamhomes.haven.offer.exception.OfferNotFoundException;
import com.dreamhomes.haven.inspection.service.InspectionService;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.model.OfferStatus;
@Service
@Slf4j
@RequiredArgsConstructor
public class OfferService {

    private static final String DEFAULT_CURRENCY = "NGN";

    private final OfferRepository offerRepository;
    private final ListingService listingService;
    private final OutboxEventRepository outboxRepository;
    private final NotificationApi notificationApi;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public Offer submit(Long applicantId, SubmitOfferCommand cmd) {
        // Throws ListingNotFoundException if missing — surfaces as RFC 7807 404.
        ListingResponse listing = listingService.findById(cmd.listingId());
        if (listing.status() != ListingStatus.LIVE) {
            throw new ListingNotOpenForOffersException();
        }

        Offer saved = offerRepository.save(Offer.builder()
                .listingId(listing.id())
                .applicantId(applicantId)
                .ownerId(listing.ownerId())
                .amount(cmd.amount())
                .currency(cmd.currency() != null ? cmd.currency() : DEFAULT_CURRENCY)
                .message(cmd.message())
                .status(OfferStatus.PENDING)
                // Original offer: applicant proposed it. The owner is the one who
                // will accept/decline/counter. Counter-offers (Phase 13) flip this.
                .proposedByUserId(applicantId)
                .build());

        // Outbox + offer commit together. The OutboxRelay ships to Kafka asynchronously.
        UUID eventId = UUID.randomUUID();
        // Domain timestamp (the event's occurredAt) — separate from row-create auditing.
        Instant occurredAt = Instant.now();
        OfferSubmittedEvent event = new OfferSubmittedEvent(
                eventId,
                saved.getId(),
                listing.id(),
                listing.ownerId(),
                applicantId,
                saved.getAmount(),
                saved.getCurrency(),
                occurredAt);
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("Offer")
                .aggregateId(saved.getId())
                .eventType(OfferSubmittedEvent.class.getName())
                .topic(OfferSubmittedEvent.TOPIC)
                .partitionKey(String.valueOf(listing.id()))  // per-listing ordering
                .payload(serialize(event))
                .build());

        // Drain right after this transaction commits — see InspectionService for rationale.
        applicationEventPublisher.publishEvent(OutboxRowReadyEvent.INSTANCE);

        log.info("Submitted offerId={} listingId={} applicantId={} amount={} eventId={}",
                saved.getId(), listing.id(), applicantId, saved.getAmount(), eventId);
        return saved;
    }

    /**
     * Accept or decline. Authorisation: caller must be a participant (owner or
     * applicant on this offer's listing) AND must NOT be the one who proposed this
     * specific row — counter-offers strictly alternate.
     */
    @Transactional
    public Offer respond(Long callerId, Long offerId, OfferStatus newStatus) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new OfferNotFoundException(offerId));

        if (!isParticipant(offer, callerId)) {
            throw new NotPropertyOwnerException();
        }
        if (offer.getProposedByUserId().equals(callerId)) {
            throw new CannotActOnOwnOfferException();
        }
        if (!isAllowedTransition(offer.getStatus(), newStatus)) {
            throw new InvalidOfferTransitionException(offer.getStatus(), newStatus);
        }

        offer.setStatus(newStatus);
        // updatedAt is bumped by JPA auditing on save (entity has @LastModifiedDate).
        Offer saved = offerRepository.save(offer);

        // When one offer wins, every PENDING sibling on the same listing loses. Flip them
        // to DECLINED in the same transaction and notify their applicants — otherwise
        // those rows sit forever and the losing applicant never finds out.
        if (newStatus == OfferStatus.ACCEPTED) {
            autoDeclineSiblings(saved);
        }

        log.info("Caller {} responded to offerId={} with status={}", callerId, offerId, newStatus);
        return saved;
    }

    private void autoDeclineSiblings(Offer accepted) {
        List<Offer> siblings = offerRepository.findByListingIdAndStatusAndIdNot(
                accepted.getListingId(), OfferStatus.PENDING, accepted.getId());
        if (siblings.isEmpty()) {
            return;
        }
        for (Offer sibling : siblings) {
            sibling.setStatus(OfferStatus.DECLINED);
            offerRepository.save(sibling);
            notifyAutoDeclined(sibling, accepted.getId());
        }
        log.info("Auto-declined {} sibling offer(s) on listingId={} after offerId={} accepted",
                siblings.size(), accepted.getListingId(), accepted.getId());
    }

    private void notifyAutoDeclined(Offer sibling, Long winningOfferId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offerId", sibling.getId());
        payload.put("listingId", sibling.getListingId());
        payload.put("winningOfferId", winningOfferId);
        payload.put("reason", "ANOTHER_OFFER_ACCEPTED");
        notificationApi.recordSync(NotificationKind.OFFER_AUTO_DECLINED, sibling.getApplicantId(), payload);
    }

    /**
     * Phase 13 — counter-offer. The parent goes COUNTERED (terminal-but-tracked); a new
     * child Offer is inserted with the new amount + parent_offer_id pointing back. The
     * child's {@code proposedByUserId} is the caller, so the OTHER party becomes the
     * one allowed to act on the new row. Sync notification (no Kafka) — caller and
     * recipient are the same two users already present in the chain, no async fan-out
     * needed.
     */
    @Transactional
    public Offer counter(Long callerId, Long parentOfferId, BigDecimal newAmount, String message) {
        if (newAmount == null || newAmount.signum() <= 0) {
            throw new IllegalArgumentException("Counter amount must be positive");
        }
        Offer parent = offerRepository.findById(parentOfferId)
                .orElseThrow(() -> new OfferNotFoundException(parentOfferId));

        if (!isParticipant(parent, callerId)) {
            throw new NotPropertyOwnerException();
        }
        if (parent.getProposedByUserId().equals(callerId)) {
            throw new CannotActOnOwnOfferException();
        }
        if (parent.getStatus() != OfferStatus.PENDING) {
            throw new InvalidOfferTransitionException(parent.getStatus(), OfferStatus.COUNTERED);
        }

        // Mark parent as COUNTERED — terminal but tracked in history.
        // updatedAt is bumped by JPA auditing on save.
        parent.setStatus(OfferStatus.COUNTERED);
        offerRepository.save(parent);

        // New child offer flips proposedBy to the caller. createdAt + updatedAt populated by auditing.
        Offer child = offerRepository.save(Offer.builder()
                .listingId(parent.getListingId())
                .applicantId(parent.getApplicantId())
                .ownerId(parent.getOwnerId())
                .amount(newAmount)
                .currency(parent.getCurrency())
                .message(message)
                .status(OfferStatus.PENDING)
                .proposedByUserId(callerId)
                .parentOfferId(parent.getId())
                .build());

        // Notify the other party (whoever isn't the caller).
        Long otherParty = callerId.equals(parent.getOwnerId())
                ? parent.getApplicantId()
                : parent.getOwnerId();
        notifyOfferCountered(otherParty, child);

        log.info("Caller {} countered offerId={} → childOfferId={} amount={}",
                callerId, parentOfferId, child.getId(), newAmount);
        return child;
    }

    private static boolean isParticipant(Offer offer, Long callerId) {
        return callerId.equals(offer.getOwnerId()) || callerId.equals(offer.getApplicantId());
    }

    /**
     * PENDING is the only state from which {@link #respond} or {@link #counter} can
     * transition. ACCEPTED, DECLINED, and COUNTERED are all terminal.
     */
    private static boolean isAllowedTransition(OfferStatus from, OfferStatus to) {
        return from == OfferStatus.PENDING
                && (to == OfferStatus.ACCEPTED || to == OfferStatus.DECLINED);
    }

    @Transactional(readOnly = true)
    public boolean hadAcceptedOffer(Long listingId, Long applicantUserId) {
        return offerRepository.existsByListingIdAndApplicantIdAndStatus(
                listingId, applicantUserId, OfferStatus.ACCEPTED);
    }

    private void notifyOfferCountered(Long recipientId, Offer child) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offerId", child.getId());
        payload.put("parentOfferId", child.getParentOfferId());
        payload.put("listingId", child.getListingId());
        payload.put("amount", child.getAmount());
        payload.put("proposedByUserId", child.getProposedByUserId());
        notificationApi.recordSync(NotificationKind.OFFER_COUNTERED, recipientId, payload);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }
}
