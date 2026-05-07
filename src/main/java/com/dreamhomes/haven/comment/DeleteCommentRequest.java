package com.dreamhomes.haven.comment;

import jakarta.validation.constraints.Size;

/**
 * Optional reason for deletion. Authors deleting their own comment can omit this; admins
 * and listing owners are encouraged (not enforced) to supply one for the audit trail.
 */
public record DeleteCommentRequest(
        @Size(max = 1000) String reason
) {
}
