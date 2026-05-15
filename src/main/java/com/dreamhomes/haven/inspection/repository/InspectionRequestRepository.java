package com.dreamhomes.haven.inspection.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;

import java.util.Collection;

public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, Long> {

    /** Backs {@code GET /api/inspections/mine}: applicant's own requests, newest first. */
    Page<InspectionRequest> findByApplicantIdOrderByCreatedAtDesc(Long applicantId, Pageable pageable);

    /**
     * True when another active request already holds this slot (partial unique index
     * on {@code slot_id} for {@code PENDING}/{@code APPROVED}), excluding {@code excludeId}.
     */
    boolean existsBySlotIdAndStatusInAndIdNot(Long slotId,
                                              Collection<InspectionRequestStatus> statuses,
                                              Long excludeId);
}
