package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers what JwtService composes on top of JJWT — not JJWT itself.
 *
 * <ul>
 *   <li>Our claim layout (sub, email, role, tv) round-trips through issue/parse.</li>
 *   <li>Our explicit {@code requireIssuer}/{@code requireAudience} on the parser
 *       reject foreign-issued tokens — without those calls, JJWT happily accepts them.</li>
 *   <li>Our constructor guards (length, placeholder substrings) fail fast on misconfig.</li>
 *   <li>Our TTL math produces an expiry within the configured window.</li>
 * </ul>
 *
 * <p>Signature tampering, expiry enforcement, and HMAC verification are JJWT primitives;
 * they have their own tests upstream.
 */
class JwtServiceTest {

    private static final String SECRET = "this-is-a-test-secret-of-at-least-32-bytes-okay-yes";
    private static final Duration TTL = Duration.ofHours(1);
    private static final String ISSUER = "dreamhomes-haven-test";
    private static final String AUDIENCE = "dreamhomes-test";

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, TTL, ISSUER, AUDIENCE);
    }

    @Test
    void issuedTokenRoundTripsAllClaimsWeSet() {
        String token = jwtService.issue(42L, "ada@example.com", Role.AGENT, 7);

        JwtPrincipal principal = jwtService.parse(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("ada@example.com");
        assertThat(principal.role()).isEqualTo(Role.AGENT);
        assertThat(principal.tokenVersion()).isEqualTo(7);
    }

    @Test
    void parserRejectsTokenWithDifferentIssuer() {
        JwtService foreignIssuer = new JwtService(SECRET, TTL, "some-other-app", AUDIENCE);
        String token = foreignIssuer.issue(1L, "x@example.com", Role.OWNER, 1);

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
    }

    @Test
    void parserRejectsTokenWithDifferentAudience() {
        JwtService foreignAud = new JwtService(SECRET, TTL, ISSUER, "another-audience");
        String token = foreignAud.issue(1L, "x@example.com", Role.OWNER, 1);

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
    }

    @Test
    void constructorRejectsKnownPlaceholderSecrets() {
        assertThatThrownBy(() -> new JwtService("change-me-to-a-secure-secret-of-at-least-32-bytes", TTL, ISSUER, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder");
        assertThatThrownBy(() -> new JwtService("REPLACE-ME-with-a-real-secret-of-at-least-32-bytes", TTL, ISSUER, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void constructorRejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService("too-short", TTL, ISSUER, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void issuedTokenExpiryFallsWithinConfiguredTtlWindow() {
        Instant beforeIssue = Instant.now();
        String token = jwtService.issue(1L, "x@example.com", Role.OWNER, 1);

        Instant expiry = jwtService.parseExpiry(token);
        assertThat(expiry).isAfter(beforeIssue.plus(TTL).minusSeconds(2));
        assertThat(expiry).isBefore(beforeIssue.plus(TTL).plusSeconds(2));
    }
}
