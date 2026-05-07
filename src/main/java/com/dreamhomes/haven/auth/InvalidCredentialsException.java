package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
