package com.dreamhomes.haven.review.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Reviewer already reviewed this counterparty on this listing. Reviews are immutable in
 * Phase 10 — to update a review the user would have to wait for a future edit endpoint.
 */
public class DuplicateReviewException extends DomainException {

    public DuplicateReviewException() {
        super(HttpStatus.CONFLICT, "You've already reviewed this person for this listing");
    }
}
