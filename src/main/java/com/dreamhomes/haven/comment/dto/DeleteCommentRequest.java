package com.dreamhomes.haven.comment.dto;

import jakarta.validation.constraints.Size;

public record DeleteCommentRequest(
        @Size(max = 1000) String reason
) {
}
