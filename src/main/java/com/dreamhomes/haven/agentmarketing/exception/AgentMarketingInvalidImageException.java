package com.dreamhomes.haven.agentmarketing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentMarketingInvalidImageException extends DomainException {

    public AgentMarketingInvalidImageException(String safeMessage) {
        super(HttpStatus.BAD_REQUEST, safeMessage);
    }
}
