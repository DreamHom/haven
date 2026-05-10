package com.dreamhomes.haven.inspection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.inspection.model.InspectionRequest;

public interface InspectionRequestRepository extends JpaRepository<InspectionRequest, Long> {
}
