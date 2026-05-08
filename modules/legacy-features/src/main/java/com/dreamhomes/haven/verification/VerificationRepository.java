package com.dreamhomes.haven.verification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRepository extends JpaRepository<Verification, Long> {

    /** Backs the admin queue: oldest pending first, scoped by type. */
    Page<Verification> findByTypeAndStatusOrderBySubmittedAtAsc(
            VerificationType type, VerificationStatus status, Pageable pageable);

    /**
     * Pre-flight check the service runs to short-circuit the duplicate-submission path
     * with a clean domain exception. The partial unique index in V10 is the actual
     * guarantee — this just avoids hitting the constraint when we can.
     */
    boolean existsByTypeAndTargetUserIdAndStatus(
            VerificationType type, Long targetUserId, VerificationStatus status);

    boolean existsByTypeAndTargetPropertyIdAndStatus(
            VerificationType type, Long targetPropertyId, VerificationStatus status);
}
