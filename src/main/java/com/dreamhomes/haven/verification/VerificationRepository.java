package com.dreamhomes.haven.verification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;

public interface VerificationRepository extends JpaRepository<Verification, Long> {

    /** Backs the admin queue: oldest pending first, scoped by type. */
    Page<Verification> findByTypeAndStatusOrderBySubmittedAtAsc(
            VerificationType type, VerificationStatus status, Pageable pageable);

    /** Backs {@code GET /api/verifications/mine}: caller's own submissions, newest first. */
    Page<Verification> findBySubmitterUserIdOrderBySubmittedAtDesc(
            Long submitterUserId, Pageable pageable);

    /**
     * Pre-flight check the service runs to short-circuit the duplicate-submission path
     * with a clean domain exception. The partial unique index in V10 is the actual
     * guarantee — this just avoids hitting the constraint when we can.
     */
    boolean existsByTypeAndTargetUserIdAndStatus(
            VerificationType type, Long targetUserId, VerificationStatus status);

    boolean existsByTypeAndTargetPropertyIdAndStatus(
            VerificationType type, Long targetPropertyId, VerificationStatus status);

    /** Aggregate count by status — backs the admin analytics summary. */
    long countByStatus(VerificationStatus status);
}
