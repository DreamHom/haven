package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.user.model.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers what JwtService composes on top of JJWT — not JJWT itself.
 *
 * <ul>
 *   <li>Our claim layout (sub, email, role, tv) round-trips through issue/parse with RS256.</li>
 *   <li>Our explicit {@code requireIssuer}/{@code requireAudience} on the parser
 *       reject foreign-issued tokens — without those calls, JJWT happily accepts them.</li>
 *   <li>Our constructor guards (RSA-only, &gt;= 2048 bits, matching keypair) fail fast on misconfig.</li>
 *   <li>Our TTL math produces an expiry within the configured window.</li>
 *   <li>A token signed by a different keypair is rejected (signature verification works as wired).</li>
 * </ul>
 *
 * <p>RSA primitives, expiry enforcement, and the actual signature math are JJWT primitives;
 * they have their own tests upstream.
 */
class JwtServiceTest {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String ISSUER = "dreamhomes-haven-test";
    private static final String AUDIENCE = "dreamhomes-test";

    static KeyPair keyPair;
    static KeyPair otherKeyPair;

    JwtService jwtService;

    @BeforeAll
    static void generateKeypairs() throws Exception {
        // Real 2048-bit RSA so our constructor's bit-length guard passes; cheap enough at @BeforeAll.
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        otherKeyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(keyPair.getPrivate(), keyPair.getPublic(), TTL, ISSUER, AUDIENCE);
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
        JwtService foreignIssuer = new JwtService(keyPair.getPrivate(), keyPair.getPublic(),
                TTL, "some-other-app", AUDIENCE);
        String token = foreignIssuer.issue(1L, "x@example.com", Role.OWNER, 1);

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
    }

    @Test
    void parserRejectsTokenWithDifferentAudience() {
        JwtService foreignAud = new JwtService(keyPair.getPrivate(), keyPair.getPublic(),
                TTL, ISSUER, "another-audience");
        String token = foreignAud.issue(1L, "x@example.com", Role.OWNER, 1);

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
    }

    @Test
    void parserRejectsTokenSignedByDifferentKeypair() {
        JwtService rogue = new JwtService(otherKeyPair.getPrivate(), otherKeyPair.getPublic(),
                TTL, ISSUER, AUDIENCE);
        String token = rogue.issue(1L, "x@example.com", Role.OWNER, 1);

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    void constructorRejectsMismatchedKeypair() {
        // Public key from one pair, private from another — verifying anything signed will fail,
        // and we'd rather discover that at startup than at first auth.
        assertThatThrownBy(() -> new JwtService(keyPair.getPrivate(), otherKeyPair.getPublic(),
                TTL, ISSUER, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modulus");
    }

    @Test
    void constructorRejectsKeyShorterThan2048Bits() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024);
        KeyPair weak = gen.generateKeyPair();

        assertThatThrownBy(() -> new JwtService(weak.getPrivate(), weak.getPublic(),
                TTL, ISSUER, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2048");
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
