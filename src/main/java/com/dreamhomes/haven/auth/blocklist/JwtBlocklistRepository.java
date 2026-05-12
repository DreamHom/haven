package com.dreamhomes.haven.auth.blocklist;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JwtBlocklistRepository extends JpaRepository<JwtBlocklistEntry, UUID> {

    /**
     * Hot path — called from the auth filter on every authenticated request. Index-only
     * lookup via the {@code jti} primary key, so the cost is one B-tree probe.
     */
    boolean existsByJti(UUID jti);
}
