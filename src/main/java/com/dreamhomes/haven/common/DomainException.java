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
    private final String typeSuffix;

    protected DomainException(HttpStatus status, String safeMessage) {
        this(status, safeMessage, null);
    }

    /**
     * Subclasses that want a specific {@code type} URI suffix beyond the generic
     * status-family default (e.g. "listing.duplicate-open-listing-for-property-and-type"
     * instead of just "conflict") can pass it here. {@link com.dreamhomes.haven.common.web.GlobalExceptionHandler}
     * appends it to {@code haven.errors.type-base} verbatim.
     */
    protected DomainException(HttpStatus status, String safeMessage, String typeSuffix) {
        super(safeMessage);
        this.status = status;
        this.typeSuffix = typeSuffix;
    }

    public HttpStatus status() {
        return status;
    }

    /** Optional custom suffix appended to the error type base. Null means use the status default. */
    public String typeSuffix() {
        return typeSuffix;
    }
}
