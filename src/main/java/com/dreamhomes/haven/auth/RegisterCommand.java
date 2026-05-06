package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;

public record RegisterCommand(
        String email,
        String password,
        String fullName,
        String phone,
        Role role
) {
}
