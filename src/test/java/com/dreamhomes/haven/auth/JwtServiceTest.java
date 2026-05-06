package com.dreamhomes.haven.auth;

import com.dreamhomes.haven.user.Role;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "this-is-a-test-secret-of-at-least-32-bytes-okay-yes";
    private static final Duration TTL = Duration.ofHours(1);

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, TTL);
    }

    @Test
    void issuedTokenRoundTripsToOriginalSubjectAndRole() {
        String token = jwtService.issue(42L, "ada@example.com", Role.AGENT);

        JwtPrincipal principal = jwtService.parse(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("ada@example.com");
        assertThat(principal.role()).isEqualTo(Role.AGENT);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issue(1L, "x@example.com", Role.OWNER);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(SECRET, Duration.ofSeconds(-1));
        String expired = shortLived.issue(1L, "x@example.com", Role.OWNER);

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void tokenIssuedWithDifferentSecretIsRejected() {
        JwtService other = new JwtService("totally-different-secret-of-at-least-32-bytes-x", TTL);
        String foreignToken = other.issue(1L, "x@example.com", Role.OWNER);

        assertThatThrownBy(() -> jwtService.parse(foreignToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void issuedTokenHasExpiryInFuture() {
        Instant beforeIssue = Instant.now();
        String token = jwtService.issue(1L, "x@example.com", Role.OWNER);

        Instant expiry = jwtService.parseExpiry(token);
        assertThat(expiry).isAfter(beforeIssue.plus(TTL).minusSeconds(2));
        assertThat(expiry).isBefore(beforeIssue.plus(TTL).plusSeconds(2));
    }
}
