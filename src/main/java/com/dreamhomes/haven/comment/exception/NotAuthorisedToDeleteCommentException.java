package com.dreamhomes.haven.comment.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller is none of: the comment's author, the listing's owner, an admin. Surface 403
 * with a generic message — the same response shape regardless of who the caller is.
 */
public class NotAuthorisedToDeleteCommentException extends DomainException {

    public NotAuthorisedToDeleteCommentException() {
        super(HttpStatus.FORBIDDEN, "You aren't authorised to delete this comment");
    }
}
