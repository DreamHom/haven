package com.dreamhomes.haven.photo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddPhotoRequest(
        @NotBlank @Size(max = 512) String url,
        @Size(max = 255) String caption
) {
}
