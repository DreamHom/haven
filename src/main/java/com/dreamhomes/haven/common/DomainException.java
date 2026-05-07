package com.dreamhomes.haven.common;

import org.springframework.http.HttpStatus;

/**
 * Base for exceptions thrown by domain logic that map to a specific HTTP status.
 *
 * <p>Concrete subclasses carry the status they want; {@link com.dreamhomes.haven.common.web.GlobalExceptionHandler}
 * turns them into RFC 7807 problem responses without per-class boilerplate.
 *
 * <p>Messages on these exceptions are sent to clients verbatim — never include user input
 * or anything that could leak existence/identity (e.g. "email already registered: ada@…"
 * confirms the email is registered).
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;

    protected DomainException(HttpStatus status, String safeMessage) {
        super(safeMessage);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
