package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import com.dreamhomes.haven.notification.Notification;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.notification.NotificationSource;
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
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OfferService {

    private static final String DEFAULT_CURRENCY = "NGN";

    private final OfferRepository offerRepository;
    private final ListingRepository listingRepository;
    private final OutboxEventRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public Offer submit(Long applicantId, SubmitOfferCommand cmd) {
        Listing listing = listingRepository.findById(cmd.listingId())
                .orElseThrow(() -> new ListingNotFoundException(cmd.listingId()));
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new ListingNotOpenForOffersException();
        }

        Instant now = Instant.now();
        Offer saved = offerRepository.save(Offer.builder()
                .listingId(listing.getId())
                .applicantId(applicantId)
                .ownerId(listing.getOwnerId())
                .amount(cmd.amount())
                .currency(cmd.currency() != null ? cmd.currency() : DEFAULT_CURRENCY)
                .message(cmd.message())
                .status(OfferStatus.PENDING)
                // Original offer: applicant proposed it. The owner is the one who
                // will accept/decline/counter. Counter-offers (Phase 13) flip this.
                .proposedByUserId(applicantId)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Outbox + offer commit together. The OutboxRelay ships to Kafka asynchronously.
        UUID eventId = UUID.randomUUID();
        OfferSubmittedEvent event = new OfferSubmittedEvent(
                eventId,
                saved.getId(),
                listing.getId(),
                listing.getOwnerId(),
                applicantId,
                saved.getAmount(),
                saved.getCurrency(),
                now);
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("Offer")
                .aggregateId(saved.getId())
                .eventType(OfferSubmittedEvent.class.getName())
                .topic(OfferSubmittedEvent.TOPIC)
                .partitionKey(String.valueOf(listing.getId()))  // per-listing ordering
                .payload(serialize(event))
                .createdAt(now)
                .build());

        // Drain right after this transaction commits — see InspectionService for rationale.
        applicationEventPublisher.publishEvent(OutboxRowReadyEvent.INSTANCE);

        log.info("Submitted offerId={} listingId={} applicantId={} amount={} eventId={}",
                saved.getId(), listing.getId(), applicantId, saved.getAmount(), eventId);
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
        offer.setUpdatedAt(Instant.now());
        Offer saved = offerRepository.save(offer);
        log.info("Caller {} responded to offerId={} with status={}", callerId, offerId, newStatus);
        return saved;
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

        Instant now = Instant.now();
        // Mark parent as COUNTERED — terminal but tracked in history.
        parent.setStatus(OfferStatus.COUNTERED);
        parent.setUpdatedAt(now);
        offerRepository.save(parent);

        // New child offer flips proposedBy to the caller.
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
                .createdAt(now)
                .updatedAt(now)
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

    private void notifyOfferCountered(Long recipientId, Offer child) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offerId", child.getId());
        payload.put("parentOfferId", child.getParentOfferId());
        payload.put("listingId", child.getListingId());
        payload.put("amount", child.getAmount());
        payload.put("proposedByUserId", child.getProposedByUserId());
        notificationRepository.save(Notification.builder()
                .recipientId(recipientId)
                .kind(NotificationKind.OFFER_COUNTERED)
                .source(NotificationSource.SYNC)
                .payload(serializeMap(payload))
                .createdAt(Instant.now())
                .build());
    }

    private String serializeMap(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise counter-offer notification payload", e);
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }
}
