package com.dreamhomes.haven.domain.offer.controller;

import com.dreamhomes.haven.domain.offer.dto.CounterOfferRequest;
import com.dreamhomes.haven.domain.offer.dto.OfferResponse;
import com.dreamhomes.haven.domain.offer.dto.SubmitOfferRequest;
import com.dreamhomes.haven.domain.offer.service.OfferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse submit(@Valid @RequestBody SubmitOfferRequest req) {
        var o = offerService.submit(req);
        return new OfferResponse(o.getId(), o.getListingId(), o.getApplicantId(), o.getAmount(), o.getStatus(), o.getCreatedAt());
    }

    @PutMapping("/{id}/counter")
    public OfferResponse counter(@PathVariable Long id, @Valid @RequestBody CounterOfferRequest req) {
        var o = offerService.counter(id, req);
        return new OfferResponse(o.getId(), o.getListingId(), o.getApplicantId(), o.getAmount(), o.getStatus(), o.getCreatedAt());
    }
}

