package com.dreamhomes.haven.auth.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when registration cannot complete because an account with the same email exists.
 *
 * <p>The message is intentionally generic — leaking the email back to the caller would let
 * an attacker enumerate valid emails by hammering /register. The status code itself
 * (409) still carries some signal; mitigating that fully would require an email-driven
 * confirmation flow ("we'll send a link if this email is new") which we don't have yet.
 */
public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "Registration could not be completed");
    }
}
