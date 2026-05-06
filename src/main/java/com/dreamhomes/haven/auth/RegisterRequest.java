package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 32) String phone,
        @NotNull Role role
) {
    /** PRD: admins are seeded only — never accept ADMIN role through self-registration. */
    @AssertTrue(message = "role must not be ADMIN")
    public boolean isPublicRole() {
        return role != Role.ADMIN;
    }

    public RegisterCommand toCommand() {
        return new RegisterCommand(email, password, fullName, phone, role);
    }
}
