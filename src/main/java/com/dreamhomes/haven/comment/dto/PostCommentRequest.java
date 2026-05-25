package com.dreamhomes.haven.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCommentRequest(
        @NotBlank @Size(max = 4000) String body,

        /**
         * Optional reply target — when supplied, this comment is a reply to the referenced
         * top-level comment. The parent must exist, be non-deleted, and belong to the same
         * listing; otherwise the post is rejected (404 if missing, 400 otherwise). Omit
         * for a top-level comment.
         */
        @Schema(description = """
                Optional parent comment id. When supplied, this comment is a reply to that \
                parent. The parent must exist (else 404), must not be soft-deleted, and must \
                belong to the same listing (else 400). Vista builds the comment tree client-side.
                """, example = "5", nullable = true)
        Long parentCommentId
) {
}
