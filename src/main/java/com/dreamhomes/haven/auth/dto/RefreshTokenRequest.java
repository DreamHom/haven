package com.dreamhomes.haven.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/refresh}. The opaque refresh token the
 * client stored from a prior {@code /login} or {@code /refresh} response.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {
}
