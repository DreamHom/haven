package com.dreamhomes.haven.dreamai.chat.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/** Chat id missing or not owned by the caller. */
public class DreamAiChatNotFoundException extends DomainException {

    public DreamAiChatNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Dream AI chat not found.");
    }
}
