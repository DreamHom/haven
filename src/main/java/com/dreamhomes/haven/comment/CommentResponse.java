package com.dreamhomes.haven.comment;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long listingId,
        Long authorUserId,
        String body,
        Instant createdAt
) {
}
