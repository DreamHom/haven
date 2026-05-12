package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentProfileNotFoundException extends DomainException {

    public AgentProfileNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "Agent profile not found");
    }
}
