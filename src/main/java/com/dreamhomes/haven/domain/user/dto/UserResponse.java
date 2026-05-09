package com.dreamhomes.haven.domain.user.dto;

import com.dreamhomes.haven.domain.user.model.Role;

public record UserResponse(
        Long id,
        String email,
        Role role,
        String firstName,
        String lastName,
        String displayName
) {}

