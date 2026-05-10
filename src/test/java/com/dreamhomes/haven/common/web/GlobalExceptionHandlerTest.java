package com.dreamhomes.haven.common.web;

import com.dreamhomes.haven.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import com.dreamhomes.haven.listing.model.Listing;

/**
 * Direct unit tests of the mappings we own. The Spring-driven exception-resolution path
 * (binding @ExceptionHandler to a real request) is framework — covered by integration
 * tests of the controllers. Here we just prove each handler returns the right status.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void domainExceptionMapsToTheStatusItDeclares() {
        ProblemDetail result = handler.handleDomain(new SampleException());

        assertThat(result.getStatus()).isEqualTo(HttpStatus.I_AM_A_TEAPOT.value());
        assertThat(result.getDetail()).isEqualTo("teapot is short");
    }

    @Test
    void optimisticLockingFailureMapsTo409WithGenericMessage() {
        ProblemDetail result = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Listing", 1L));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getDetail()).contains("modified by someone else");
    }

    private static final class SampleException extends DomainException {
        SampleException() {
            super(HttpStatus.I_AM_A_TEAPOT, "teapot is short");
        }
    }
}
