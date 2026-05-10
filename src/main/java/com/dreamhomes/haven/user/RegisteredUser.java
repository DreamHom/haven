package com.dreamhomes.haven.user;

import java.time.Instant;

/**
 * Result of a successful {@link UserCredentialsService#create(NewUser)} call. Carries
 * the persisted id + timestamp so auth-impl can assemble its post-register wire
 * response without ever seeing the {@code User} entity.
 */
public record RegisteredUser(Long id, Instant createdAt) {
}
