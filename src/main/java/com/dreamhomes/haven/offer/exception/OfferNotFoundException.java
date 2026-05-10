package com.dreamhomes.haven.offer.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.offer.model.Offer;
public class OfferNotFoundException extends DomainException {

    public OfferNotFoundException(Long offerId) {
        super(HttpStatus.NOT_FOUND, "Offer " + offerId + " was not found");
    }
}
