package com.dreamhomes.haven.common;

import org.springframework.http.HttpStatus;

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
