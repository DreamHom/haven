package com.dreamhomes.haven.user;

/**
 * Auth-flow projection of a user. Carries exactly what the login + JWT-validation
 * paths need; deliberately no name, phone, or other PII the auth feature has no
 * business handling.
 *
 * <p>Returned by {@link UserCredentialsService#loadByEmail(String)} and built from the
 * canonical {@code User} entity inside {@code feature/user/impl}. The auth feature
 * never sees the entity.</p>
 */
public record UserCredentials(
        Long id,
        String email,
        String passwordHash,
        Role role,
        int tokenVersion,
        boolean suspended) {
}
