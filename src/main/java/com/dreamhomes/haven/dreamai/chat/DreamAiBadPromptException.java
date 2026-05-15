package com.dreamhomes.haven.dreamai.chat;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class DreamAiBadPromptException extends DomainException {

    public DreamAiBadPromptException() {
        super(HttpStatus.BAD_REQUEST, "Provide `prompt` and/or `userChoice.sendText`.");
    }
}
