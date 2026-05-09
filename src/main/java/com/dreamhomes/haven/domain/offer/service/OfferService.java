package com.dreamhomes.haven.domain.offer.service;

import com.dreamhomes.haven.domain.offer.dto.CounterOfferRequest;
import com.dreamhomes.haven.domain.offer.dto.SubmitOfferRequest;
import com.dreamhomes.haven.domain.offer.event.OfferEventProducer;
import com.dreamhomes.haven.domain.offer.event.OfferSubmittedEvent;
import com.dreamhomes.haven.domain.offer.model.Offer;
import com.dreamhomes.haven.domain.offer.model.OfferStatus;
import com.dreamhomes.haven.domain.offer.repository.OfferRepository;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {

    private final OfferRepository offerRepository;
    private final OfferEventProducer eventProducer;
    private final String offerSubmittedTopic;

    public OfferService(
            OfferRepository offerRepository,
            OfferEventProducer eventProducer,
            @Value("${kafka.topics.offer-submitted:OFFER_SUBMITTED}") String offerSubmittedTopic
    ) {
        this.offerRepository = offerRepository;
        this.eventProducer = eventProducer;
        this.offerSubmittedTopic = offerSubmittedTopic;
    }

    @Transactional
    public Offer submit(SubmitOfferRequest req) {
        var o = new Offer();
        o.setListingId(req.listingId());
        o.setApplicantId(req.applicantId());
        o.setAmount(req.amount());
        var saved = offerRepository.save(o);

        eventProducer.publishOfferSubmitted(
                offerSubmittedTopic,
                new OfferSubmittedEvent(saved.getId(), saved.getListingId(), saved.getApplicantId(), saved.getAmount(), Instant.now())
        );
        return saved;
    }

    @Transactional
    public Offer counter(Long offerId, CounterOfferRequest req) {
        var o = offerRepository.findById(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        o.setAmount(req.counterAmount());
        o.setStatus(OfferStatus.COUNTERED);
        return offerRepository.save(o);
    }
}