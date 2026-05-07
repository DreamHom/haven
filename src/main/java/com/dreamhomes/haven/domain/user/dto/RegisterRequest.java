package com.dreamhomes.haven.domain.user.dto;

import com.dreamhomes.haven.domain.user.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        @NotNull Role role,
        String displayName
) {}

