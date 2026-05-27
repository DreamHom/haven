package com.dreamhomes.haven.verification.automation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationAutomationResultRepository
        extends JpaRepository<VerificationAutomationResult, Long> {

    /** Backs the admin queue UI: every automated check that ran for this verification. */
    List<VerificationAutomationResult> findByVerificationIdOrderByRunAtAsc(Long verificationId);
}
