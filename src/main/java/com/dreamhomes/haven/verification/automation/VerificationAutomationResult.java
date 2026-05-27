package com.dreamhomes.haven.verification.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted result of one {@link VerificationProvider} call against a verification row.
 * One verification can have multiple rows (e.g. owner identity check + future liveness
 * fold-in). Admins read these in the queue UI so they can corroborate the documents
 * with what the provider extracted.
 */
@Entity
@Table(name = "verification_automation_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationAutomationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verification_id", nullable = false)
    private Long verificationId;

    @Column(name = "check_type", nullable = false, length = 64)
    private String checkType;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", columnDefinition = "jsonb")
    private String extractedFields;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "run_at", nullable = false, updatable = false)
    private Instant runAt;
}
