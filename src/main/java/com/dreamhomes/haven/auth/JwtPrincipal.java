package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;

/**
 * The information the auth chain trusts from a verified JWT, before any DB lookup.
 *
 * <p>{@code tokenVersion} is the value that was embedded in the JWT at issuance; the
 * filter cross-checks it against the user's current value to enforce revocation.
 */
public record JwtPrincipal(Long userId, String email, Role role, int tokenVersion) {
}
