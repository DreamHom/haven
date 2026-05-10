package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;

import java.time.Instant;
import com.dreamhomes.haven.user.model.User;

/**
 * Public response shape for the post-register and authenticated-self endpoints. Plain
 * record — construction lives in {@code feature-auth-impl} since this DTO is in -api
 * and never sees the {@code User} entity.
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        Instant createdAt
) {
}
