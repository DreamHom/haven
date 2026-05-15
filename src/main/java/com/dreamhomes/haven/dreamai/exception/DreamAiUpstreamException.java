package com.dreamhomes.haven.dreamai.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Anthropic returned an error, timed out, or produced output we could not parse into listing ids.
 */
public class DreamAiUpstreamException extends DomainException {

    public DreamAiUpstreamException(String safeMessage) {
        super(HttpStatus.BAD_GATEWAY, safeMessage);
    }
}
