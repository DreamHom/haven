package com.dreamhomes.haven.comment.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Caller tried to reply to a parent that is either soft-deleted or belongs to a
 * different listing. 400 — the request is malformed: parent existence was confirmed but
 * the parent does not satisfy the reply-target invariants (same listing + still active).
 *
 * <p>404 is reserved for the parent-not-found case (see
 * {@link com.dreamhomes.haven.comment.exception.CommentNotFoundException}); we keep them
 * distinct so the client UI can branch — "this reply target is gone" vs "this reply
 * target was never valid".
 */
public class InvalidParentCommentException extends DomainException {

    public InvalidParentCommentException(String reason) {
        super(HttpStatus.BAD_REQUEST, "Invalid parent comment: " + reason);
    }
}
