package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;
// import com.dreamhomes.haven.auth.service.AuthService;

public record RegisterCommand(
        String email,
        String password,
        String fullName,
        String displayName,
        String phone,
        Role role,
        String licenseNumber
) {
}
