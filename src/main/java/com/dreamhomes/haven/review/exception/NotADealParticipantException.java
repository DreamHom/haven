package com.dreamhomes.haven.review.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller wasn't a participant in the deal — neither the listing's owner nor an applicant
 * with an ACCEPTED offer. 403 with a generic message; never reveal who actually was the
 * counterparty.
 */
public class NotADealParticipantException extends DomainException {

    public NotADealParticipantException() {
        super(HttpStatus.FORBIDDEN, "You weren't a participant in this deal");
    }
}
