package com.dreamhomes.haven.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String displayName
) {}

