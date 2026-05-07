package com.dreamhomes.haven.domain.user.dto;

public record AuthResponse(
        String accessToken,
        String tokenType
) {}

