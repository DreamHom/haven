package com.dreamhomes.haven.domain.verification.repository;

import com.dreamhomes.haven.domain.verification.model.Verification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationRepository extends JpaRepository<Verification, Long> {}

