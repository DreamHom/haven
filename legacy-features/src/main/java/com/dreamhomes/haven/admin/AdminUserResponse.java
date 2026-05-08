package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        Role role,
        Instant suspendedAt,
        Instant identityVerifiedAt
) {
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(), u.getEmail(), u.getRole(),
                u.getSuspendedAt(), u.getIdentityVerifiedAt());
    }
}
