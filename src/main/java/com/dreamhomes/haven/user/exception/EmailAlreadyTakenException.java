package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.user.dto.NewUser;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import com.dreamhomes.haven.auth.exception.EmailAlreadyRegisteredException;
/**
 * Thrown by {@link UserCredentialsService#create(NewUser)} when the email is already
 * registered. Auth-impl catches this and rethrows its own
 * {@code EmailAlreadyRegisteredException} so the HTTP-facing wire message stays
 * stable for /register; other consumers can surface it directly. Both ultimately
 * resolve to 409 via {@code GlobalExceptionHandler}.
 */
public class EmailAlreadyTakenException extends DomainException {

    public EmailAlreadyTakenException() {
        super(HttpStatus.CONFLICT, "Email is already registered");
    }
}
