package com.dreamhomes.haven.domain.comment.dto;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long listingId,
        Long userId,
        String body,
        Instant createdAt
) {}

