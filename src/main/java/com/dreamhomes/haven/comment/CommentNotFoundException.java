package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class CommentNotFoundException extends DomainException {

    public CommentNotFoundException(Long commentId) {
        super(HttpStatus.NOT_FOUND, "Comment " + commentId + " was not found");
    }
}
