package com.dreamhomes.haven.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
        @NotNull Long listingId,
        @NotNull Long userId,
        @NotBlank String body
) {}

