package com.dreamhomes.haven.auth.dto;

import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.auth.service.AuthService;
/**
 * Inputs to {@link AuthService#register} — already validated at the controller layer
 * by {@link RegisterRequest}'s Bean Validation rules. Service code can trust them.
 */
public record RegisterCommand(
        String email,
        String password,
        String fullName,
        /** Optional; service defaults to the first token of fullName when null/blank. */
        String displayName,
        String phone,
        Role role,
        String licenseNumber
) {
}
