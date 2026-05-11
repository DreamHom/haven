package com.dreamhomes.haven.comment.dto;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long listingId,
        Long authorUserId,
        String body,
        Instant createdAt
) {
}
