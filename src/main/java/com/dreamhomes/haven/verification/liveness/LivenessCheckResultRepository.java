package com.dreamhomes.haven.verification.liveness;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LivenessCheckResultRepository extends JpaRepository<LivenessCheckResult, Long> {
}
