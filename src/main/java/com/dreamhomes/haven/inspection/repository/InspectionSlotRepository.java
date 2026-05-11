package com.dreamhomes.haven.inspection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;
import com.dreamhomes.haven.inspection.model.InspectionSlot;

public interface InspectionSlotRepository extends JpaRepository<InspectionSlot, Long> {

    /**
     * Returns slots on the given listing that have no active (PENDING or APPROVED)
     * inspection request — i.e., slots still bookable. Ordered by start time so the
     * frontend can render a chronological calendar without sorting client-side.
     */
    @Query("""
            SELECT s FROM InspectionSlot s
            WHERE s.listingId = :listingId
              AND NOT EXISTS (
                SELECT 1 FROM InspectionRequest r
                WHERE r.slotId = s.id
                  AND r.status IN (com.dreamhomes.haven.inspection.model.InspectionRequestStatus.PENDING,
                                   com.dreamhomes.haven.inspection.model.InspectionRequestStatus.APPROVED)
              )
            ORDER BY s.startsAt ASC
            """)
    List<InspectionSlot> findAvailableForListing(Long listingId);
}
