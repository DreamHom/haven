package com.dreamhomes.haven.agentmarketing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentMarketingInvalidOrderException extends DomainException {

    public AgentMarketingInvalidOrderException(String safeMessage) {
        super(HttpStatus.BAD_REQUEST, safeMessage);
    }
}
