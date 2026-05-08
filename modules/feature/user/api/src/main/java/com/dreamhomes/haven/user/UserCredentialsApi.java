package com.dreamhomes.haven.user;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Auth-facing slice of the user feature. Replaces what was previously a direct
 * {@code UserRepository} dependency from {@code feature/auth/impl} — that direct
 * impl-impl edge was the original "auth + user share an aggregate" exception in
 * {@code TRADEOFFS.md}. This api collapses the exception: auth-impl now compiles
 * against {@code feature/user/api} only, like every other consumer.
 *
 * <p>Surface kept deliberately narrow — only what the login, registration, logout,
 * and JWT-validation paths actually need.</p>
 */
public interface UserCredentialsApi {

    /**
     * Login lookup. Returns the credential bundle for the given email, or empty if
     * no user exists. Email is expected to be already-normalised (lowercase, trimmed)
     * by the caller — auth owns that normalisation.
     */
    Optional<UserCredentials> loadByEmail(String email);

    /**
     * Pre-flight check used by {@code AuthService.register} to surface a clean
     * {@code 409 EmailAlreadyRegistered} before the BCrypt encode step.
     */
    boolean existsByEmail(String email);

    /**
     * JWT-filter token-version check. Returns the current {@code tokenVersion} for the
     * user, or empty if the user no longer exists (token was issued, then user got
     * deleted — reject the token).
     */
    OptionalInt tokenVersionOf(Long userId);

    /**
     * Atomically increment the user's token version. Used by logout (and by admin
     * suspension via {@link UserAdminApi}). Idempotent in the sense that calling
     * twice in a row simply bumps twice — every outstanding JWT is already invalid
     * after the first call.
     *
     * <p>Returns the new version; useful for logging. If the user doesn't exist this
     * is a no-op (logout against a deleted account succeeds silently).</p>
     */
    OptionalInt bumpTokenVersion(Long userId);

    /**
     * Create the user (and an {@code AgentProfile} when {@code role == AGENT}) atomically.
     * Returns the new user's id + persisted createdAt timestamp so the caller can
     * assemble its wire response without seeing the {@code User} entity.
     *
     * @throws EmailAlreadyTakenException if the email is taken (covers the
     *         post-encode TOCTOU race where two parallel registrations slip past the
     *         {@link #existsByEmail(String)} pre-check).
     */
    RegisteredUser create(NewUser newUser);
}
