package com.dreamhomes.haven.comment.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class CommentFlagNotFoundException extends DomainException {

    public CommentFlagNotFoundException(Long flagId) {
        super(HttpStatus.NOT_FOUND, "Comment flag " + flagId + " was not found");
    }
}
