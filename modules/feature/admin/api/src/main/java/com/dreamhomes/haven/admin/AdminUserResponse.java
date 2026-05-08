package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.user.Role;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String email,
        Role role,
        Instant suspendedAt,
        Instant identityVerifiedAt
) {
}
