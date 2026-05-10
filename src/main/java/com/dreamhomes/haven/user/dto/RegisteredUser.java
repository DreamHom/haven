package com.dreamhomes.haven.user.dto;

import java.time.Instant;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.service.UserCredentialsService;

/**
 * Result of a successful {@link UserCredentialsService#create(NewUser)} call. Carries
 * the persisted id + timestamp so auth-impl can assemble its post-register wire
 * response without ever seeing the {@code User} entity.
 */
public record RegisteredUser(Long id, Instant createdAt) {
}
