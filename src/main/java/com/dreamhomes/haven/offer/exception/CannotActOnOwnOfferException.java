package com.dreamhomes.haven.offer.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller is the same party that proposed the latest offer in the chain — they have to
 * wait for the other party to act. Counter-offers strictly alternate.
 */
public class CannotActOnOwnOfferException extends DomainException {

    public CannotActOnOwnOfferException() {
        super(HttpStatus.FORBIDDEN, "You can't act on your own offer; wait for the other party to respond");
    }
}
