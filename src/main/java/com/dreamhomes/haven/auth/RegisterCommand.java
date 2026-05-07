package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;

/**
 * Inputs to {@link AuthService#register} — already validated at the controller layer
 * by {@link RegisterRequest}'s Bean Validation rules. Service code can trust them.
 */
public record RegisterCommand(
        String email,
        String password,
        String fullName,
        String phone,
        Role role,
        String licenseNumber
) {
}
