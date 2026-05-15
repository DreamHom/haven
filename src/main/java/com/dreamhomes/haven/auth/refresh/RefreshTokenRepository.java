package com.dreamhomes.haven.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke every still-active refresh row for a user — the path called by
     * full-account logout and by password reset. Uses a bulk UPDATE rather
     * than load-then-save so this stays O(1) round-trips no matter how many
     * sessions a user has.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken r
               SET r.revokedAt = :now
             WHERE r.userId = :userId
               AND r.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
