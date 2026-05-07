package com.dreamhomes.haven.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record UserModerationRequest(
        @NotBlank String action
) {}