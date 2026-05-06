package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;

public record JwtPrincipal(Long userId, String email, Role role) {
}
