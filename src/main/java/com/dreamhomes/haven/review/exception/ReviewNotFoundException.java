package com.dreamhomes.haven.review.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class ReviewNotFoundException extends DomainException {

    public ReviewNotFoundException(Long reviewId) {
        super(HttpStatus.NOT_FOUND, "Review " + reviewId + " was not found");
    }
}
