package com.dreamhomes.haven.auth.service;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    /** Minimum RSA key size we'll accept. NIST recommends 2048+ for RSA today. */
    private static final int MIN_RSA_KEY_BITS = 2048;

    private final PrivateKey signingKey;
    private final PublicKey verificationKey;
    private final Duration ttl;
    private final String issuer;
    private final String audience;

    @Autowired
    public JwtService(@Value("${haven.jwt.private-key}") String privateKeyPem,
                      @Value("${haven.jwt.public-key}") String publicKeyPem,
                      @Value("${haven.jwt.expiration-ms}") long expirationMs,
                      @Value("${haven.jwt.issuer}") String issuer,
                      @Value("${haven.jwt.audience}") String audience) {
        this(parsePrivateKey(privateKeyPem), parsePublicKey(publicKeyPem),
                Duration.ofMillis(expirationMs), issuer, audience);
    }

    // public so JwtServiceTest (in com.dreamhomes.haven.auth) can construct directly.
    public JwtService(PrivateKey signingKey, PublicKey verificationKey,
                      Duration ttl, String issuer, String audience) {
        if (!(signingKey instanceof RSAKey rsaPriv)) {
            throw new IllegalArgumentException("haven.jwt.private-key must be an RSA private key");
        }
        if (!(verificationKey instanceof RSAKey rsaPub)) {
            throw new IllegalArgumentException("haven.jwt.public-key must be an RSA public key");
        }
        if (rsaPriv.getModulus().bitLength() < MIN_RSA_KEY_BITS
                || rsaPub.getModulus().bitLength() < MIN_RSA_KEY_BITS) {
            throw new IllegalArgumentException(
                    "haven.jwt RSA key length must be at least " + MIN_RSA_KEY_BITS + " bits");
        }
        if (!rsaPriv.getModulus().equals(rsaPub.getModulus())) {
            // The public key has to verify what the private key signs — same modulus is the
            // cheap check that they're a real keypair, not two unrelated keys mashed together.
            throw new IllegalArgumentException(
                    "haven.jwt private and public keys do not share the same RSA modulus");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("haven.jwt.issuer must be set");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("haven.jwt.audience must be set");
        }
        this.signingKey = signingKey;
        this.verificationKey = verificationKey;
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
                .signWith(signingKey, Jwts.SIG.RS256)
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
                .verifyWith(verificationKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("haven.jwt.private-key must be set (PEM, PKCS#8)");
        }
        try {
            byte[] der = stripPemHeaders(pem, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "haven.jwt.private-key is not a valid PKCS#8 PEM-encoded RSA private key", e);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("haven.jwt.public-key must be set (PEM, X.509 SPKI)");
        }
        try {
            byte[] der = stripPemHeaders(pem, "PUBLIC KEY");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "haven.jwt.public-key is not a valid X.509 SPKI PEM-encoded RSA public key", e);
        }
    }

    private static byte[] stripPemHeaders(String pem, String label) {
        String body = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }
}
