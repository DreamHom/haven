package com.dreamhomes.haven.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A single verification submission. Owners, agents, and applicants submit their own
 * identity rows; owners submit property document rows on behalf of properties they
 * own. Admins decide each row exactly once — re-submitting after rejection creates
 * a new row, leaving the rejected one as audit history.
 *
 * <p>The {@code verifications_decision_complete} CHECK constraint pairs
 * {@code status != PENDING} with non-null {@code decided_at} and
 * {@code decided_by_admin_id}; the service must populate both atomically.
 */
@Entity
@Table(name = "verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationType type;

    @Column(name = "submitter_user_id", nullable = false)
    private Long submitterUserId;

    /** Set for OWNER_IDENTITY / AGENT_CREDENTIALS / APPLICANT_IDENTITY; null for PROPERTY_DOCUMENTS. */
    @Column(name = "target_user_id")
    private Long targetUserId;

    /** Set only for PROPERTY_DOCUMENTS. */
    @Column(name = "target_property_id")
    private Long targetPropertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    /**
     * Free-shape JSON metadata about the submitted documents — kind/ref pairs only,
     * no raw files (PRD §6: "All sensitive document references stored as metadata only").
     * Stored as JSONB so the column type matches the migration; the service feeds it a
     * pre-serialised string so we don't depend on Hibernate JSONB converters here.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_refs", nullable = false, columnDefinition = "jsonb")
    private String documentRefs;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by_admin_id")
    private Long decidedByAdminId;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    /** Optimistic lock — guards against two admins racing to decide the same row. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
