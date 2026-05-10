package com.dreamhomes.haven.common.web;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.offer.model.Offer;
/**
 * Maps {@link DomainException} subclasses to RFC 7807 problem responses with the status
 * each exception declares. Validation (400) and authentication (401) are handled by
 * Spring's defaults; this advice only owns domain-specific failures.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        return ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    }

    /**
     * Optimistic lock conflicts on Listing/Offer (or any future {@code @Version}-locked
     * entity) surface as 409 — same shape as our other duplicate/conflict paths so
     * clients have one retry/recovery story.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This resource was modified by someone else — reload and retry");
    }
}
