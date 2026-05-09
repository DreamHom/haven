package com.dreamhomes.haven.domain.inspection.service;

import com.dreamhomes.haven.domain.inspection.dto.CreateSlotRequest;
import com.dreamhomes.haven.domain.inspection.model.InspectionSlot;
import com.dreamhomes.haven.domain.inspection.repository.InspectionSlotRepository;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionSlotService {
    private final InspectionSlotRepository slotRepository;

    @Transactional
    public InspectionSlot create(CreateSlotRequest req) {
        var slot = new InspectionSlot();
        slot.setListingId(req.listingId());
        slot.setAgentId(req.agentId());
        slot.setStartAt(req.startAt());
        slot.setEndAt(req.endAt());
        
        return slotRepository.save(slot);
    }

    @Transactional(readOnly = true)
    public InspectionSlot get(Long id) {
        return slotRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inspection slot not found"));
    }
}

