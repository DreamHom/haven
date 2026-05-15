package com.dreamhomes.haven.auth.blocklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per revoked JWT. Backs the per-device logout flow — the auth filter rejects any
 * inbound JWT whose jti has a row here. Pruned offline once {@code expiresAt} is in the past
 * (the token would expire anyway, so the row is no longer load-bearing).
 */
@Entity
@Table(name = "jwt_blocklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtBlocklistEntry {

    @Id
    @Column(name = "jti", nullable = false)
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;
}
