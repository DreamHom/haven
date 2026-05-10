package com.dreamhomes.haven.user.dto;

import com.dreamhomes.haven.user.model.AgentProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
/**
 * Input to {@link UserCredentialsService#create(NewUser)}. The auth feature pre-hashes
 * the password (it owns the encoder + DUMMY_HASH timing-attack defence) and hands
 * the rest of the registration payload to user-impl, which atomically inserts the
 * user row plus an {@code AgentProfile} row when the role is {@code AGENT}.
 *
 * <p>{@code licenseNumber} is null for non-AGENT registrations.</p>
 */
public record NewUser(
        String email,
        String passwordHash,
        Role role,
        String fullName,
        /** Required at this layer — {@code AuthService.register} computes a default
         *  from {@code fullName} when the caller doesn't supply one, so by the time
         *  the user-impl layer sees it, it's always set. */
        String displayName,
        String phone,
        String licenseNumber) {
}
