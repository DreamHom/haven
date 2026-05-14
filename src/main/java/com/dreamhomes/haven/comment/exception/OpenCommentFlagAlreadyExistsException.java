package com.dreamhomes.haven.comment.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class OpenCommentFlagAlreadyExistsException extends DomainException {

    public OpenCommentFlagAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "You already have an open flag on this comment");
    }
}
