package com.dreamhomes.haven.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long listingId,
        Long authorUserId,
        String body,
        @Schema(description = """
                Parent comment id for replies. Null = top-level comment. Vista assembles \
                the threaded tree client-side by grouping responses on parentCommentId.
                """, example = "5", nullable = true)
        Long parentCommentId,
        Instant createdAt
) {
}
