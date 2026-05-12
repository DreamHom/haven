package com.dreamhomes.haven.user.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Per-role profile data for users with {@link Role#AGENT}. Lives in its own table
 * (Option C: shared identity + per-role profile) so agent-only columns don't pollute
 * the {@code users} identity table with NULLs for non-agents.
 *
 * <p>The primary key IS the user id — there is no separate sequence — so the FK and
 * PK are the same column. Cascade-delete is enforced at the DB level (V2 migration);
 * we don't model the inverse navigation from User → AgentProfile to keep User lean.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "agent_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "license_number", nullable = false, unique = true, length = 64)
    private String licenseNumber;

    @Column(name = "cac_registration_number", length = 64)
    private String cacRegistrationNumber;

    @Column(length = 255)
    private String agency;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @CreatedDate

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when an admin approves an AGENT_CREDENTIALS verification for this profile. */
    @Column(name = "credential_verified_at")
    private Instant credentialVerifiedAt;
}
