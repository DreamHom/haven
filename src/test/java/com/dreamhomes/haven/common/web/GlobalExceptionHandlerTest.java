package com.dreamhomes.haven.common.web;

import com.dreamhomes.haven.dreamai.moderation.DreamAiModerationBlockedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamhomes.haven.common.DomainException;

/**
 * Direct unit tests of the mappings we own. The Spring-driven exception-resolution path
 * (binding @ExceptionHandler to a real request) is framework — covered by integration
 * tests of the controllers. Here we just prove each handler returns the right status.
 */
class GlobalExceptionHandlerTest {

    private static final String TYPE_BASE = "https://github.com/DreamHom/haven/blob/main/docs/errors/";
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(TYPE_BASE);

    @Test
    void domainExceptionMapsToTheStatusItDeclares() {
        ProblemDetail result = handler.handleDomain(new SampleException());

        assertThat(result.getStatus()).isEqualTo(HttpStatus.I_AM_A_TEAPOT.value());
        assertThat(result.getDetail()).isEqualTo("teapot is short");
        // Default-branch fallback in typeFor() — anything not in the family map gets "domain-error".
        assertThat(result.getType().toString()).isEqualTo(TYPE_BASE + "domain-error");
    }

    @Test
    void optimisticLockingFailureMapsTo409WithGenericMessage() {
        ProblemDetail result = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Listing", 1L));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(result.getDetail()).contains("modified by someone else");
        assertThat(result.getType().toString()).isEqualTo(TYPE_BASE + "conflict");
    }

    @Test
    void domainException422MapsToModerationBlockedType() {
        ProblemDetail result = handler.handleDomain(new DreamAiModerationBlockedException("no"));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(result.getType().toString()).isEqualTo(TYPE_BASE + "moderation-blocked");
    }

    private static final class SampleException extends DomainException {
        SampleException() {
            super(HttpStatus.I_AM_A_TEAPOT, "teapot is short");
        }
    }
}
