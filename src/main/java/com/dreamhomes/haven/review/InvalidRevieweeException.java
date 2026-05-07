package com.dreamhomes.haven.review;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * The reviewee isn't a valid counterparty — e.g. owner trying to review someone who
 * never had an accepted offer on their listing, or applicant reviewing someone other
 * than the listing's owner. Same 403 shape as {@link NotADealParticipantException} so
 * the wire response doesn't leak which condition failed.
 */
public class InvalidRevieweeException extends DomainException {

    public InvalidRevieweeException() {
        super(HttpStatus.FORBIDDEN, "Reviewee isn't a counterparty in this deal");
    }
}
