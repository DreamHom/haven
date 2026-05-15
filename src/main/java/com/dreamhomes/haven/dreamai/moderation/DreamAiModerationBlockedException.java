package com.dreamhomes.haven.dreamai.moderation;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class DreamAiModerationBlockedException extends DomainException {

    public DreamAiModerationBlockedException(String safeMessage) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, safeMessage);
    }
}
