package com.dreamhomes.haven.comment.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.comment.Comment;


public class CommentAlreadyDeletedException extends DomainException {

    public CommentAlreadyDeletedException(Long commentId) {
        super(HttpStatus.CONFLICT, "Comment " + commentId + " is already deleted");
    }
}
