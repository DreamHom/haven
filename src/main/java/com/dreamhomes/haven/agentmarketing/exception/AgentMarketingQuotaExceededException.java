package com.dreamhomes.haven.agentmarketing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class AgentMarketingQuotaExceededException extends DomainException {

    public AgentMarketingQuotaExceededException(int max) {
        super(HttpStatus.BAD_REQUEST, "Marketing gallery is limited to " + max + " images");
    }
}
