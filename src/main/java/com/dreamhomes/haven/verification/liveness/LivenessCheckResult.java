package com.dreamhomes.haven.verification.liveness;

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
 * One liveness check attempt for a user — captured as its own row so we can audit (and
 * in v2 retry) without coupling to the {@code verifications} table. v1 always writes
 * {@code provider_name = "MOCK"} + {@code status = "PASSED"} + {@code score = 0.97};
 * v2 swaps the provider while keeping the same row shape so callers (and the verification
 * submit path) are unchanged.
 *
 * <p>{@code consumed_at} is the replay guard. The verification submit endpoint stamps
 * it the first time a liveness id is referenced — a subsequent submit referencing the
 * same id surfaces a 409 from {@code LivenessCheckService.consume}.
 *
 * <p>See Item 19 in {@code docs/demo-prep/post-session-tasks.md} for the v1/v2 framing.
 */
@Entity
@Table(name = "liveness_check_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LivenessCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column
    private BigDecimal score;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    /**
     * Provider's raw response payload — JSONB so v2 providers can dump their full
     * verification body for forensics. v1 mock writes a small canned blob.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
