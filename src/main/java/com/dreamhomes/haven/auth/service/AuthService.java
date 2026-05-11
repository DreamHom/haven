package com.dreamhomes.haven.auth.service;

import com.dreamhomes.haven.user.exception.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.dto.NewUser;
import com.dreamhomes.haven.user.dto.RegisteredUser;
import com.dreamhomes.haven.user.dto.UserCredentials;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import com.dreamhomes.haven.auth.dto.LoginCommand;
import com.dreamhomes.haven.auth.dto.RegisterCommand;
import com.dreamhomes.haven.auth.exception.InvalidCredentialsException;

/**
 * Login + registration + logout. Talks to the user feature exclusively via
 * {@link UserCredentialsService} — no direct repository access. The "auth and user share
 * an aggregate" exception that {@code TRADEOFFS.md} used to document is collapsed:
 * auth-impl no longer compiles against user-impl, and the {@code BannedDependencies}
 * enforcer now applies to this module like every other feature-impl.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    /**
     * A real BCrypt hash of a fixed input, computed once at class load. When a login
     * is attempted for a missing email we still run {@code matches} against this hash
     * so wall-clock cost is the same as for an existing user with a wrong password —
     * preventing email enumeration via response timing. Computed (not hard-coded) so
     * the hash always satisfies BCrypt's pattern check and reaches the constant-time
     * compare path inside {@link BCryptPasswordEncoder}.
     */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("never-matches");

    private final UserCredentialsService userCredentialsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String login(LoginCommand cmd) {
        String email = normalize(cmd.email());
        Optional<UserCredentials> maybe = userCredentialsService.loadByEmail(email);
        String hashToCheck = maybe.map(UserCredentials::passwordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(cmd.password(), hashToCheck);
        if (maybe.isEmpty() || !passwordMatches) {
            log.warn("Login failed for email='{}'", email);
            throw new InvalidCredentialsException();
        }
        UserCredentials creds = maybe.get();
        // Suspended users have valid credentials but can't get a fresh JWT — surface the
        // same 401 as a bad password so the response shape stays uniform across reasons.
        if (creds.suspended()) {
            log.warn("Login rejected for suspended userId={}", creds.id());
            throw new InvalidCredentialsException();
        }
        log.info("Login succeeded for userId={} role={}", creds.id(), creds.role());
        return jwtService.issue(creds.id(), creds.email(), creds.role(), creds.tokenVersion());
    }

    /**
     * Bumps the user's {@code tokenVersion} so every previously-issued JWT for this user
     * is rejected by the auth filter on the next request. Idempotent: calling twice in a
     * row produces tokens-already-invalid, which is fine.
     */
    public void logout(Long userId) {
        userCredentialsService.bumpTokenVersion(userId);
        log.info("Logged out userId={}", userId);
    }

    /**
     * Returns indistinguishably (no exception, no return value) whether or not the email
     * was already taken. The controller surfaces 202 Accepted in both cases — that's the
     * anti-enumeration contract: an attacker can't tell from the API whether an email is
     * registered. Real existence-check + insert still happens for new emails; for taken
     * emails we log and swallow.
     */
    public void register(RegisterCommand cmd) {
        String email = normalize(cmd.email());
        if (userCredentialsService.existsByEmail(email)) {
            log.info("Register noop: email='{}' already registered (returning 202 to avoid enumeration)", email);
            return;
        }
        String displayName = defaultDisplayName(cmd.displayName(), cmd.fullName());
        try {
            RegisteredUser registered = userCredentialsService.create(new NewUser(
                    email,
                    passwordEncoder.encode(cmd.password()),
                    cmd.role(),
                    cmd.fullName(),
                    displayName,
                    cmd.phone(),
                    cmd.licenseNumber()));
            log.info("Registered userId={} role={}", registered.id(), cmd.role());
        } catch (EmailAlreadyTakenException race) {
            // TOCTOU: existsByEmail returned false but the colliding insert landed first.
            // Swallow — same anti-enumeration contract as the up-front duplicate check.
            log.info("Register lost race for email='{}' (returning 202 to avoid enumeration)", email);
        }
    }

    /** Emails are case-insensitive identifiers — store and look them up in lowercase. */
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Pick a sensible displayName when the caller didn't supply one. First
     * whitespace-delimited token of fullName works for the most common Nigerian-name
     * shapes ("Amaka Chinwe Okafor" → "Amaka") and matches the V19 backfill rule
     * applied to existing rows, so behaviour is consistent across registration paths
     * and historical data.
     */
    private static String defaultDisplayName(String supplied, String fullName) {
        if (supplied != null && !supplied.isBlank()) {
            return supplied.trim();
        }
        String trimmed = fullName.trim();
        int firstSpace = trimmed.indexOf(' ');
        return firstSpace < 0 ? trimmed : trimmed.substring(0, firstSpace);
    }
}
