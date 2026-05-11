package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.user.dto.NewUser;
import com.dreamhomes.haven.user.service.UserCredentialsService;

/**
 * Thrown by {@link UserCredentialsService#create(NewUser)} when the email is already
 * registered. Auth-impl now catches this in its register flow and silently swallows it
 * (the controller surfaces 202 in either branch — the anti-enumeration contract); other
 * consumers can surface it directly via {@code GlobalExceptionHandler} as 409.
 */
public class EmailAlreadyTakenException extends DomainException {

    public EmailAlreadyTakenException() {
        super(HttpStatus.CONFLICT, "Email is already registered");
    }
}
