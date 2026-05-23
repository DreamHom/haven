package com.dreamhomes.haven.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Optional reporter note when flagging a comment.")
public record FlagCommentRequest(
        @Size(max = 512)
        String reason
) {
}
