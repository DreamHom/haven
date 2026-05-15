package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.common.validation.StrictEmail;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank @StrictEmail String email
) {
}
