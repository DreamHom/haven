package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.outbox.OutboxEvent;
import com.dreamhomes.haven.common.outbox.OutboxEventRepository;
import com.dreamhomes.haven.common.outbox.OutboxRowReadyEvent;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import com.dreamhomes.haven.offer.events.OfferSubmittedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OfferService {

    private static final String DEFAULT_CURRENCY = "NGN";

    private final OfferRepository offerRepository;
    private final ListingRepository listingRepository;
    private final OutboxEventRepository outboxRepository;
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

    @Transactional
    public Offer respond(Long callerId, Long offerId, OfferStatus newStatus) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new OfferNotFoundException(offerId));
        if (!offer.getOwnerId().equals(callerId)) {
            throw new NotPropertyOwnerException();
        }
        if (!isAllowedTransition(offer.getStatus(), newStatus)) {
            throw new InvalidOfferTransitionException(offer.getStatus(), newStatus);
        }

        offer.setStatus(newStatus);
        offer.setUpdatedAt(Instant.now());
        Offer saved = offerRepository.save(offer);
        log.info("Owner {} responded to offerId={} with status={}", callerId, offerId, newStatus);
        return saved;
    }

    /** Only PENDING → ACCEPTED or PENDING → DECLINED is allowed. Everything else is no-op or backwards. */
    private static boolean isAllowedTransition(OfferStatus from, OfferStatus to) {
        return from == OfferStatus.PENDING
                && (to == OfferStatus.ACCEPTED || to == OfferStatus.DECLINED);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }
}
