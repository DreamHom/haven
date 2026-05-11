package com.dreamhomes.haven.auth.service;

import com.dreamhomes.haven.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import com.dreamhomes.haven.auth.JwtPrincipal;

@Service
public class JwtService {

    /** Substrings that almost certainly indicate a forgotten placeholder, not a real secret. */
    private static final String[] PLACEHOLDER_MARKERS = {
            "change-me", "CHANGE-ME", "replace-me", "REPLACE-ME", "DEV_ONLY", "placeholder"
    };

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;
    private final String audience;

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs,
                      @Value("${jwt.issuer}") String issuer,
                      @Value("${jwt.audience}") String audience) {
        this(secret, Duration.ofMillis(expirationMs), issuer, audience);
    }

    // public so JwtServiceTest (in com.dreamhomes.haven.auth) can construct directly.
    public JwtService(String secret, Duration ttl, String issuer, String audience) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret must be at least 32 bytes");
        }
        for (String marker : PLACEHOLDER_MARKERS) {
            if (secret.contains(marker)) {
                throw new IllegalArgumentException(
                        "jwt.secret looks like a placeholder ('" + marker + "'); set JWT_SECRET to a real value");
            }
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("jwt.issuer must be set");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("jwt.audience must be set");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issue(Long userId, String email, Role role, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        Claims claims = parseClaims(token);
        return new JwtPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class)),
                claims.get("tv", Integer.class));
    }

    public Instant parseExpiry(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
