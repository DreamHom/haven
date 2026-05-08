package com.dreamhomes.haven.comment;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long listingId,
        Long authorUserId,
        String body,
        Instant createdAt
) {
    public static CommentResponse from(Comment c) {
        return new CommentResponse(
                c.getId(), c.getListingId(), c.getAuthorUserId(),
                c.getBody(), c.getCreatedAt());
    }
}
