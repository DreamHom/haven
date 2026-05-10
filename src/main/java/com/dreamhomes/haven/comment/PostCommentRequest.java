package com.dreamhomes.haven.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCommentRequest(
        @NotBlank @Size(max = 4000) String body
) {
}
