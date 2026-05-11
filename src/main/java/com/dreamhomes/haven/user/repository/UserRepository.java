package com.dreamhomes.haven.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
