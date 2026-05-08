package com.dreamhomes.haven.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(length = 32)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Bumped every time the user's tokens should be invalidated (logout, suspend, etc.).
     * Embedded in every JWT; the auth filter rejects tokens whose {@code tv} claim
     * doesn't match the current value. Defaults to 1 in DB; new entities set it explicitly.
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 1;

    /**
     * Set when an admin approves an OWNER_IDENTITY or APPLICANT_IDENTITY verification.
     * Null means "not yet verified". Stored as the moment of decision (not a boolean)
     * so the audit story is complete without joining {@code admin_audit_log} on every
     * profile read.
     */
    @Column(name = "identity_verified_at")
    private Instant identityVerifiedAt;

    /**
     * Set when an admin suspends the account. Null means "active". The auth filter
     * rejects requests from suspended users; admins set this in tandem with bumping
     * {@link #tokenVersion} to invalidate any outstanding JWTs.
     */
    @Column(name = "suspended_at")
    private Instant suspendedAt;
}
