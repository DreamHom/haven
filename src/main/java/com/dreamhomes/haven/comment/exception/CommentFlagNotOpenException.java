package com.dreamhomes.haven.comment.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class CommentFlagNotOpenException extends DomainException {

    public CommentFlagNotOpenException() {
        super(HttpStatus.CONFLICT, "This flag is not open and cannot be moderated");
    }
}
