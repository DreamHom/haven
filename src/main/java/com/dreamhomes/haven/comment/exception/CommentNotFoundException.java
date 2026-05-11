package com.dreamhomes.haven.comment.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.comment.Comment;

public class CommentNotFoundException extends DomainException {

    public CommentNotFoundException(Long commentId) {
        super(HttpStatus.NOT_FOUND, "Comment " + commentId + " was not found");
    }
}
