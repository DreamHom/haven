package com.dreamhomes.haven.user;

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
        String phone,
        String licenseNumber) {
}
