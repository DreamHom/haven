package com.dreamhomes.haven.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndAccountDeletedAtIsNull(String email);

    boolean existsByEmailAndAccountDeletedAtIsNull(String email);

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
     * {@code emailLikePattern} is a case-insensitive LIKE pattern (callers pass
     * {@code null} to skip, or a value already wrapped with {@code %} and lower-cased).
     * Building the pattern in Java avoids PostgreSQL inferring {@code bytea} for
     * {@code '%'||?||'%'} inside {@code LOWER(...)}.
     */
    @Query("""
            SELECT u FROM User u
             WHERE u.accountDeletedAt IS NULL
               AND (:role IS NULL OR u.role = :role)
               AND (:suspended IS NULL
                    OR (:suspended = TRUE AND u.suspendedAt IS NOT NULL)
                    OR (:suspended = FALSE AND u.suspendedAt IS NULL))
               AND (:emailLikePattern IS NULL OR LOWER(u.email) LIKE :emailLikePattern ESCAPE '\\')
             ORDER BY u.createdAt DESC
            """)
    Page<User> adminSearch(@Param("role") Role role,
                           @Param("suspended") Boolean suspended,
                           @Param("emailLikePattern") String emailLikePattern,
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
               AND u.accountDeletedAt IS NULL
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

    @Query("select u.publicBio from User u where u.id = :id and u.accountDeletedAt is null")
    Optional<String> findPublicBioByUserId(@Param("id") Long id);

    @Query("select u.id as ownerId, u.publicBio as publicBio from User u where u.id in :ids and u.accountDeletedAt is null")
    List<OwnerPublicBioRow> findPublicBiosByUserIds(@Param("ids") Collection<Long> ids);

    /**
     * Single-owner trust + bio row used by the listing detail endpoint to embed both
     * {@code ownerPublicBio} and {@code ownerIdentityVerifiedAt} on the response in one
     * round trip (Item 16 in post-session-tasks.md).
     */
    @Query("""
            select u.id as ownerId,
                   u.publicBio as publicBio,
                   u.identityVerifiedAt as identityVerifiedAt
              from User u
             where u.id = :id and u.accountDeletedAt is null
            """)
    Optional<OwnerTrustRow> findOwnerTrustByUserId(@Param("id") Long id);

    /**
     * Bulk variant of {@link #findOwnerTrustByUserId}. Backs the listing browse path so
     * each card can render the "Possible Scam" warning chip without an N+1 fetch.
     */
    @Query("""
            select u.id as ownerId,
                   u.publicBio as publicBio,
                   u.identityVerifiedAt as identityVerifiedAt
              from User u
             where u.id in :ids and u.accountDeletedAt is null
            """)
    List<OwnerTrustRow> findOwnerTrustByUserIds(@Param("ids") Collection<Long> ids);
}
