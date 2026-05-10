package com.dreamhomes.haven.review.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ReviewAlreadyDeletedException extends DomainException {

    public ReviewAlreadyDeletedException(Long reviewId) {
        super(HttpStatus.CONFLICT, "Review " + reviewId + " is already deleted");
    }
}
