package com.dreamhomes.haven.review.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller is neither the review's author nor an admin. The reviewee (target of the
 * review) deliberately can't delete — letting bad-rating recipients self-takedown would
 * defeat the trust signal.
 */
public class NotAuthorisedToDeleteReviewException extends DomainException {

    public NotAuthorisedToDeleteReviewException() {
        super(HttpStatus.FORBIDDEN, "You aren't authorised to delete this review");
    }
}
