package com.dreamhomes.haven.domain.inspection.repository;

import com.dreamhomes.haven.domain.inspection.model.InspectionRequest;
import com.dreamhomes.haven.domain.inspection.model.InspectionStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, Long> {
    boolean existsBySlotIdAndStatusIn(Long slotId, Collection<InspectionStatus> statuses);
}

