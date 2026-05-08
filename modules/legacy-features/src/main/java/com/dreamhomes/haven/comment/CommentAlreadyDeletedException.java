package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller tried to delete an already soft-deleted comment. 409 forces the client to
 * refresh rather than silently succeed and confuse the UI.
 */
public class CommentAlreadyDeletedException extends DomainException {

    public CommentAlreadyDeletedException(Long commentId) {
        super(HttpStatus.CONFLICT, "Comment " + commentId + " is already deleted");
    }
}
