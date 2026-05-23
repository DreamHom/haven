package com.dreamhomes.haven.auth.refresh;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted state for a single refresh token. The raw token string never leaves the
 * client — what's stored here is its SHA-256 hex digest, so a DB compromise can't
 * be replayed against the auth endpoints.
 *
 * <p>Lifecycle: issued → (used to refresh) → revoked + {@code replacedById} set →
 * (presented again) → replay detected, chain revoked.</p>
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex of the raw token (64 chars). Indexed-unique at the DB level. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null while active; set when revoked (rotation, logout, or replay-detection). */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Forward link to the successor token issued during a rotation. */
    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** True iff the token is still usable: not revoked AND not yet expired. */
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
