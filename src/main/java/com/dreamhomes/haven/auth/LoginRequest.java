package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.common.validation.StrictEmail;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @StrictEmail String email,
        @NotBlank String password
) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
