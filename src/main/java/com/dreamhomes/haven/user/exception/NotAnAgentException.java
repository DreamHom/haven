package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class NotAnAgentException extends DomainException {

    public NotAnAgentException() {
        super(HttpStatus.FORBIDDEN, "Only agents can update agent profile details");
    }
}
