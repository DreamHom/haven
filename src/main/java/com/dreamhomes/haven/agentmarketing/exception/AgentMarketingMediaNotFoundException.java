package com.dreamhomes.haven.agentmarketing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentMarketingMediaNotFoundException extends DomainException {

    public AgentMarketingMediaNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Marketing media " + id + " was not found");
    }
}
