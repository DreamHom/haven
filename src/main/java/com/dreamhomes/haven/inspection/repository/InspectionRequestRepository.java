package com.dreamhomes.haven.inspection.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.inspection.model.InspectionRequest;

public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, Long> {

    /** Backs {@code GET /api/inspections/mine}: applicant's own requests, newest first. */
    Page<InspectionRequest> findByApplicantIdOrderByCreatedAtDesc(Long applicantId, Pageable pageable);
}
