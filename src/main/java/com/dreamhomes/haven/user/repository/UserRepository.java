package com.dreamhomes.haven.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Backs the admin analytics summary — count of currently-suspended user accounts. */
    long countBySuspendedAtIsNotNull();

    /**
     * Just the IDs (not the full {@link User} rows) of every account with the given
     * role. Used by listing-report fan-out to notify all admins without loading their
     * profile data into memory.
     */
    @Query("select u.id from User u where u.role = :role")
    List<Long> findIdsByRole(Role role);

    /**
     * Backs {@code GET /api/admin/users}. All filters optional; null = wildcard.
     * {@code emailFragment} is a case-insensitive LIKE — tickets arrive with full
     * emails OR partial substrings, both work.
     * Persona audit (Dayo): "tickets arrive with emails, not IDs — probing 2-10 is not a workflow."
     */
    @Query("""
            SELECT u FROM User u
             WHERE (:role IS NULL OR u.role = :role)
               AND (:suspended IS NULL
                    OR (:suspended = TRUE AND u.suspendedAt IS NOT NULL)
                    OR (:suspended = FALSE AND u.suspendedAt IS NULL))
               AND (:emailFragment IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :emailFragment, '%')))
             ORDER BY u.createdAt DESC
            """)
    Page<User> adminSearch(@Param("role") Role role,
                           @Param("suspended") Boolean suspended,
                           @Param("emailFragment") String emailFragment,
                           Pageable pageable);

    /**
     * Backs {@code GET /api/agents}. Public agent directory — non-suspended AGENTs only.
     * {@code verified} requires {@code identity_verified_at IS NOT NULL}.
     * {@code q} matches against {@code fullName} OR {@code displayName} case-insensitively.
     * Persona audit (Biodun): "I want to invite an agent — there's no way to find one."
     */
    @Query("""
            SELECT u FROM User u
             WHERE u.role = com.dreamhomes.haven.user.model.Role.AGENT
               AND u.suspendedAt IS NULL
               AND (:verified = FALSE OR u.identityVerifiedAt IS NOT NULL)
               AND (:q IS NULL
                    OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%')))
             ORDER BY u.identityVerifiedAt DESC NULLS LAST, u.createdAt DESC
            """)
    Page<User> searchAgents(@Param("q") String q,
                            @Param("verified") boolean verified,
                            Pageable pageable);
}
