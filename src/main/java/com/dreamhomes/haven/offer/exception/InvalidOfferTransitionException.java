package com.dreamhomes.haven.offer.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.offer.model.OfferStatus;
/** Once an offer leaves PENDING (accepted or declined), no further transitions are allowed. */
public class InvalidOfferTransitionException extends DomainException {

    public InvalidOfferTransitionException(OfferStatus from, OfferStatus to) {
        super(HttpStatus.BAD_REQUEST,
                "Cannot transition offer from " + from + " to " + to);
    }
}
