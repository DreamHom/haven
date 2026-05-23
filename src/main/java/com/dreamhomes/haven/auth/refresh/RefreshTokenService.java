package com.dreamhomes.haven.auth.refresh;

import com.dreamhomes.haven.auth.exception.InvalidRefreshTokenException;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.user.dto.UserCredentials;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import com.dreamhomes.haven.auth.dto.LoginResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates, and revokes refresh tokens. Sits between {@code AuthService} and
 * the {@link RefreshTokenRepository} so the auth flow never hashes tokens by hand.
 *
 * <h2>Security choices</h2>
 *
 * <ul>
 *   <li><b>Opaque tokens</b>, not JWTs. 256 bits from {@link SecureRandom}, base64url
 *       (43 chars). Storing a hash means a DB read leaks nothing replayable.</li>
 *   <li><b>SHA-256 hash at rest</b>. The raw token is shown to the client exactly
 *       once, in the login or refresh response — never persisted, never logged.</li>
 *   <li><b>Rotation on every use</b>. Each {@link #rotate(String)} marks the old row
 *       revoked and links it forward to a fresh row via {@code replaced_by_id}.</li>
 *   <li><b>Replay detection</b>. If a row that's been revoked-by-rotation is
 *       presented again, that's a copy in the wrong hands. We revoke the entire
 *       forward chain so neither party can keep using it, and force the legitimate
 *       owner to re-login (rather than letting a session race carry on).</li>
 * </ul>
 */
@Service
@Slf4j
public class RefreshTokenService {

    /** 256-bit secret. 32 bytes random → 43 base64url chars. */
    private static final int RAW_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserCredentialsService userCredentialsService;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserCredentialsService userCredentialsService,
                               JwtService jwtService,
                               @Value("${haven.jwt.refresh-expiration-ms:2592000000}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userCredentialsService = userCredentialsService;
        this.jwtService = jwtService;
        this.ttl = Duration.ofMillis(refreshExpirationMs);
    }

    /** Lifetime of a freshly minted refresh token, surfaced on the login response. */
    public long expirationSeconds() {
        return ttl.getSeconds();
    }

    /**
     * Mint a new refresh row for {@code userId} and return the raw token + its
     * expiry. Called from {@code AuthService.login} after credentials check, and
     * from {@link #rotate(String)} as the second half of the rotation step.
     */
    @Transactional
    public IssuedRefreshToken issue(Long userId, String userAgent, String ipAddress) {
        return issue(userId, userAgent, ipAddress, /* replacedRowId */ null);
    }

    /**
     * Exchange a refresh token for a new access JWT + a new refresh token. The
     * old refresh row is marked revoked + linked forward.
     *
     * <p>Failures (unknown / expired / replayed / suspended user) all surface as
     * {@link InvalidRefreshTokenException} so the response shape is uniform.</p>
     */
    @Transactional
    public LoginResult rotate(String rawToken, String userAgent, String ipAddress) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        String hash = sha256Hex(rawToken);
        RefreshToken row = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        // Replay detection: a token that's been revoked AND has a successor was
        // already used to rotate. Seeing it again means a copy is in the wild —
        // burn the entire forward chain so neither party keeps a working session.
        if (row.getRevokedAt() != null && row.getReplacedById() != null) {
            log.warn("Refresh-token replay detected for userId={} — revoking forward chain", row.getUserId());
            revokeChainFrom(row, now);
            throw new InvalidRefreshTokenException();
        }
        if (!row.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }

        UserCredentials creds = userCredentialsService.loadById(row.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (creds.suspended()) {
            // Suspended account — kill all of this user's refreshes too so the
            // suspend can't be papered over by the still-cached refresh token.
            refreshTokenRepository.revokeAllForUser(creds.id(), now);
            throw new InvalidRefreshTokenException();
        }

        // Mint a NEW row first, then revoke the old one and forward-link it. Doing
        // it in this order keeps the chain pointer consistent if a parallel rotate
        // attempt loses the race (the second to commit will see the first's
        // successor and trip the replay path on its next use).
        IssuedRefreshToken issued = issue(creds.id(), userAgent, ipAddress, row.getId());

        String accessToken = jwtService.issue(creds.id(), creds.email(), creds.role(), creds.tokenVersion());
        log.info("Refresh rotation: userId={} issued new access + refresh", creds.id());
        return new LoginResult(
                accessToken,
                creds.id(),
                creds.role(),
                creds.fullName(),
                jwtService.expirationSeconds(),
                issued.token(),
                expirationSeconds());
    }

    /**
     * Revoke every active refresh row for a user. Called by
     * {@code AuthService.logout(scope=all)} and on password reset — anywhere the
     * security stance is "every outstanding session for this account is now dead".
     */
    @Transactional
    public void revokeAllForUser(Long userId) {
        int n = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Revoked {} active refresh tokens for userId={}", n, userId);
    }

    /**
     * Revoke a single refresh row by raw token — the per-device logout path. No-op
     * if the token doesn't match anything (avoids leaking which tokens were ever
     * valid via 404 vs 204).
     */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256Hex(rawToken)).ifPresent(row -> {
            if (row.getRevokedAt() == null) {
                row.setRevokedAt(Instant.now());
                refreshTokenRepository.save(row);
            }
        });
    }

    // ─── internals ──────────────────────────────────────────────────────────

    private IssuedRefreshToken issue(Long userId, String userAgent, String ipAddress, Long replacedRowId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String rawToken = generateRawToken();

        RefreshToken row = RefreshToken.builder()
                .userId(userId)
                .tokenHash(sha256Hex(rawToken))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .userAgent(truncate(userAgent, 255))
                .ipAddress(truncate(ipAddress, 64))
                .build();
        RefreshToken saved = refreshTokenRepository.save(row);

        if (replacedRowId != null) {
            // Mark the predecessor as revoked + forward-link it to this new row.
            refreshTokenRepository.findById(replacedRowId).ifPresent(prev -> {
                prev.setRevokedAt(now);
                prev.setReplacedById(saved.getId());
                refreshTokenRepository.save(prev);
            });
        }

        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    /**
     * Walk the {@code replaced_by_id} chain starting from {@code start}, marking
     * every still-active descendant revoked. Bounded by the chain length, which
     * grows by one per refresh — long-lived sessions cap somewhere around
     * (refresh-TTL / typical rotation interval) rows.
     */
    private void revokeChainFrom(RefreshToken start, Instant now) {
        RefreshToken cursor = start;
        int safety = 0;
        while (cursor != null && safety++ < 1_000) {
            if (cursor.getRevokedAt() == null) {
                cursor.setRevokedAt(now);
                refreshTokenRepository.save(cursor);
            }
            Long next = cursor.getReplacedById();
            cursor = next == null ? null : refreshTokenRepository.findById(next).orElse(null);
        }
    }

    private String generateRawToken() {
        byte[] buf = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * SHA-256 hex of the input. Constant-cost, no salt — refresh tokens are
     * cryptographically random 256-bit secrets, so a hash collision is the
     * birthday-paradox scenario and rainbow tables don't apply.
     */
    static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable on JVM", impossible);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
