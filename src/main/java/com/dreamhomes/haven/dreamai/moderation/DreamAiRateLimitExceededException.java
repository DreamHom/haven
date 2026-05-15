package com.dreamhomes.haven.dreamai.moderation;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/** Dream AI per-user token bucket exhausted — client should honour Retry-After when present. */
public class DreamAiRateLimitExceededException extends DomainException {

    public DreamAiRateLimitExceededException(String safeMessage) {
        super(HttpStatus.TOO_MANY_REQUESTS, safeMessage);
    }
}
