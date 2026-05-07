package com.dreamhomes.haven.common.web;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
