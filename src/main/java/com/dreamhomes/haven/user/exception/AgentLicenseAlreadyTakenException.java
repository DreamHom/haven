package com.dreamhomes.haven.user.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentLicenseAlreadyTakenException extends DomainException {

    public AgentLicenseAlreadyTakenException() {
        super(HttpStatus.CONFLICT, "License number is already in use");
    }
}
