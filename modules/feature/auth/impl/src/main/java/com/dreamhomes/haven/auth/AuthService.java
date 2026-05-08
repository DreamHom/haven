package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.EmailAlreadyTakenException;
import com.dreamhomes.haven.user.NewUser;
import com.dreamhomes.haven.user.RegisteredUser;
import com.dreamhomes.haven.user.UserCredentials;
import com.dreamhomes.haven.user.UserCredentialsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Login + registration + logout. Talks to the user feature exclusively via
 * {@link UserCredentialsApi} — no direct repository access. The "auth and user share
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

    private final UserCredentialsApi userCredentialsApi;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String login(LoginCommand cmd) {
        String email = normalize(cmd.email());
        Optional<UserCredentials> maybe = userCredentialsApi.loadByEmail(email);
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
        userCredentialsApi.bumpTokenVersion(userId);
        log.info("Logged out userId={}", userId);
    }

    public UserResponse register(RegisterCommand cmd) {
        String email = normalize(cmd.email());
        if (userCredentialsApi.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            RegisteredUser registered = userCredentialsApi.create(new NewUser(
                    email,
                    passwordEncoder.encode(cmd.password()),
                    cmd.role(),
                    cmd.fullName(),
                    cmd.phone(),
                    cmd.licenseNumber()));
            log.info("Registered userId={} role={}", registered.id(), cmd.role());
            return new UserResponse(registered.id(), email, cmd.fullName(), cmd.role(), registered.createdAt());
        } catch (EmailAlreadyTakenException race) {
            // user-api signals the post-encode TOCTOU collision; remap to auth-api's
            // wire-stable exception so the controller layer / GlobalExceptionHandler
            // sees a consistent type.
            throw new EmailAlreadyRegisteredException();
        }
    }

    /** Emails are case-insensitive identifiers — store and look them up in lowercase. */
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
