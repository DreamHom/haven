package com.dreamhomes.haven.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteReviewRequest(
        @NotBlank @Size(max = 1000) String reason
) {
}
