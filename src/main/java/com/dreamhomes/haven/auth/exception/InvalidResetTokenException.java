package com.dreamhomes.haven.auth.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidResetTokenException extends DomainException {

    public InvalidResetTokenException() {
        super(HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired");
    }
}
