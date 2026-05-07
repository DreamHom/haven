package com.dreamhomes.haven.domain.inspection.service;

import com.dreamhomes.haven.domain.inspection.dto.BookInspectionRequest;
import com.dreamhomes.haven.domain.inspection.event.InspectionEventProducer;
import com.dreamhomes.haven.domain.inspection.event.InspectionRequestedEvent;
import com.dreamhomes.haven.domain.inspection.model.InspectionRequest;
import com.dreamhomes.haven.domain.inspection.model.InspectionStatus;
import com.dreamhomes.haven.domain.inspection.repository.InspectionRequestRepository;
import com.dreamhomes.haven.exception.ConflictException;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.EnumSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionRequestService {

    private final InspectionRequestRepository requestRepository;
    private final InspectionSlotService slotService;
    private final InspectionEventProducer eventProducer;
    private final String inspectionRequestedTopic;

    public InspectionRequestService(
            InspectionRequestRepository requestRepository,
            InspectionSlotService slotService,
            InspectionEventProducer eventProducer,
            @Value("${kafka.topics.inspection-requested:INSPECTION_REQUESTED}") String inspectionRequestedTopic
    ) {
        this.requestRepository = requestRepository;
        this.slotService = slotService;
        this.eventProducer = eventProducer;
        this.inspectionRequestedTopic = inspectionRequestedTopic;
    }

    @Transactional
    public InspectionRequest book(BookInspectionRequest req) {
        // conflict prevention: don't allow multiple active bookings for the same slot
        if (requestRepository.existsBySlotIdAndStatusIn(req.slotId(), EnumSet.of(InspectionStatus.PENDING, InspectionStatus.ACCEPTED))) {
            throw new ConflictException("Inspection slot already booked");
        }

        var slot = slotService.get(req.slotId());
        if (slot == null) {
            throw new ResourceNotFoundException("Inspection slot not found");
        }

        var ir = new InspectionRequest();
        ir.setSlotId(req.slotId());
        ir.setApplicantId(req.applicantId());
        var saved = requestRepository.save(ir);

        eventProducer.publishInspectionRequested(
                inspectionRequestedTopic,
                new InspectionRequestedEvent(saved.getId(), saved.getSlotId(), saved.getApplicantId(), Instant.now())
        );

        return saved;
    }
}

