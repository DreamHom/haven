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
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    /**
     * Cities / neighbourhoods the agent operates in (e.g. {@code ["Lekki", "Yaba"]}).
     * Flat list so the FE can render chips without joining; no query reads this by
     * value yet so a TEXT[] beats a join table. DB default {@code '{}'} keeps the
     * field non-null without forcing a backfill.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "service_areas", nullable = false, columnDefinition = "TEXT[]")
    @Builder.Default
    private List<String> serviceAreas = new ArrayList<>();

    /** Languages the agent operates in (e.g. {@code ["English", "Yoruba"]}). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "languages", nullable = false, columnDefinition = "TEXT[]")
    @Builder.Default
    private List<String> languages = new ArrayList<>();

    /**
     * Free-form tags the agent self-applies to describe their niche
     * (e.g. {@code ["luxury", "rentals", "commercial"]}). Not validated against a
     * controlled vocabulary — see TRADEOFFS if structured filtering ships.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "specialization_tags", nullable = false, columnDefinition = "TEXT[]")
    @Builder.Default
    private List<String> specializationTags = new ArrayList<>();

    /**
     * Free-form fee description (e.g. {@code "5% on sale, 1 month rent commission"}).
     * Nullable because most existing rows won't have one; PRD §4.2 promises transparency
     * but doesn't constrain shape. Migrate to JSONB if structured filters become useful.
     */
    @Column(name = "fee_schedule", columnDefinition = "TEXT")
    private String feeSchedule;
}
