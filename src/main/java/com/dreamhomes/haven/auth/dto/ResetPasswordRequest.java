package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.common.validation.NotCommonPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) @NotCommonPassword String newPassword
) {
}
