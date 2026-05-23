package com.dreamhomes.haven.auth.service;

import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
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
import com.dreamhomes.haven.auth.dto.LoginResult;
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
    private final NotificationApi notificationApi;
    private final com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;
    private final com.dreamhomes.haven.auth.refresh.RefreshTokenService refreshTokenService;

    public LoginResult login(LoginCommand cmd) {
        return login(cmd, null, null);
    }

    /**
     * Login overload that captures the caller's User-Agent and IP address on the issued
     * refresh-token row. Used by the controller; the no-arg variant above is kept for
     * unit-test ergonomics.
     */
    public LoginResult login(LoginCommand cmd, String userAgent, String ipAddress) {
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
        String token = jwtService.issue(creds.id(), creds.email(), creds.role(), creds.tokenVersion());
        com.dreamhomes.haven.auth.refresh.IssuedRefreshToken refresh =
                refreshTokenService.issue(creds.id(), userAgent, ipAddress);
        return new LoginResult(token, creds.id(), creds.role(), creds.fullName(),
                jwtService.expirationSeconds(),
                refresh.token(),
                refreshTokenService.expirationSeconds());
    }

    /**
     * Exchange a refresh token for a new access JWT + a new refresh token. Thin
     * delegate to {@link com.dreamhomes.haven.auth.refresh.RefreshTokenService#rotate}
     * so the controller has a single seam for both auth-flow exits (login + refresh).
     */
    public LoginResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        return refreshTokenService.rotate(rawRefreshToken, userAgent, ipAddress);
    }

    /**
     * Bumps the user's {@code tokenVersion} so every previously-issued JWT for this user
     * is rejected by the auth filter on the next request. Idempotent: calling twice in a
     * row produces tokens-already-invalid, which is fine.
     */
    public void logout(Long userId) {
        userCredentialsService.bumpTokenVersion(userId);
        // Full-account logout: every outstanding refresh token for this user dies too.
        // Otherwise a forgotten browser tab could trade its refresh for a fresh access
        // token after the tokenVersion bump and ride right back in.
        refreshTokenService.revokeAllForUser(userId);
        log.info("Logged out userId={} scope=all (tokenVersion bumped + refresh tokens revoked)", userId);
    }

    /**
     * Per-device logout: blocklist just this JWT's jti. Other tokens this user holds
     * remain valid. {@code authorizationHeader} is the raw {@code Authorization} header
     * value the request arrived with — the filter has already validated the token, so the
     * parse here will succeed (or surface as a 500-able failure, which never happens in
     * the happy path that exercises this code).
     */
    public void logoutDevice(Long userId, String authorizationHeader, String refreshTokenIfKnown) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            // Filter would already have rejected an unauthenticated request — this is
            // defence in depth, fold to all-device logout rather than do nothing.
            logout(userId);
            return;
        }
        String token = authorizationHeader.substring("Bearer ".length());
        java.util.UUID jti = jwtService.parseJti(token);
        if (jti == null) {
            // Token pre-dates the V28 jti claim — fall back to bump-token-version.
            logout(userId);
            return;
        }
        jwtBlocklistRepository.save(com.dreamhomes.haven.auth.blocklist.JwtBlocklistEntry.builder()
                .jti(jti)
                .userId(userId)
                .expiresAt(jwtService.parseExpiry(token))
                .revokedAt(java.time.Instant.now())
                .build());
        // If the client sent the refresh token alongside the device-logout call,
        // revoke that one row too so the device can't trade it for a new access JWT
        // ten seconds later. No-op when the client didn't send one.
        if (refreshTokenIfKnown != null && !refreshTokenIfKnown.isBlank()) {
            refreshTokenService.revokeByRawToken(refreshTokenIfKnown);
        }
        log.info("Logged out userId={} scope=device (jti={} blocklisted)", userId, jti);
    }

    /** Backwards-compatible 2-arg form for callers that don't supply a refresh token. */
    public void logoutDevice(Long userId, String authorizationHeader) {
        logoutDevice(userId, authorizationHeader, null);
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
            notificationApi.recordSync(NotificationKind.WELCOME, registered.id(),
                    java.util.Map.of("fullName", cmd.fullName(), "role", cmd.role().name()));
        } catch (EmailAlreadyTakenException race) {
            // TOCTOU: existsByEmail returned false but the colliding insert landed first.
            // Swallow — same anti-enumeration contract as the up-front duplicate check.
            log.info("Register lost race for email='{}' (returning 202 to avoid enumeration)", email);
        } catch (org.springframework.dao.DataIntegrityViolationException collision) {
            // AGENT registration enforces a unique license_number on agent_profiles.
            // A duplicate license is just as identity-leaky as a duplicate email — silently
            // swallow under the same anti-enumeration contract. Persona audit (Emeka):
            // re-running the bru flow with the same license number used to surface as a 500
            // forwarded to /error → 401, looking like a server crash.
            log.info("Register collision for email='{}' role={} (returning 202 to avoid enumeration): {}",
                    email, cmd.role(), collision.getMostSpecificCause().getMessage());
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
