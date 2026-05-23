package com.dreamhomes.haven.agentmarketing.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class NotYourMarketingMediaException extends DomainException {

    public NotYourMarketingMediaException() {
        super(HttpStatus.FORBIDDEN, "This marketing item does not belong to you");
    }
}
