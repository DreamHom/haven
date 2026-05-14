package com.dreamhomes.haven.comment.dto;

import com.dreamhomes.haven.comment.CommentFlagStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Moderation queue row for a flagged listing comment.")
public record CommentFlagResponse(
        @Schema(description = "Flag id.", example = "12")
        Long id,
        @Schema(description = "Listing the comment belongs to.", example = "17")
        Long listingId,
        @Schema(description = "Comment id.", example = "5")
        Long commentId,
        @Schema(description = "User who raised the flag.", example = "89")
        Long reporterUserId,
        @Schema(description = "Optional reporter reason.")
        String reason,
        @Schema(description = "Queue status.")
        CommentFlagStatus status,
        @Schema(description = "When the flag was created.")
        Instant createdAt
) {
}
