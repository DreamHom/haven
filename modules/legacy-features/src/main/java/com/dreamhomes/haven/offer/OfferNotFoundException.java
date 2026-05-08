package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class OfferNotFoundException extends DomainException {

    public OfferNotFoundException(Long offerId) {
        super(HttpStatus.NOT_FOUND, "Offer " + offerId + " was not found");
    }
}
