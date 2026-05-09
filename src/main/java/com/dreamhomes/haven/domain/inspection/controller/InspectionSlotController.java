package com.dreamhomes.haven.domain.inspection.controller;

import com.dreamhomes.haven.domain.inspection.dto.CreateSlotRequest;
import com.dreamhomes.haven.domain.inspection.dto.InspectionSlotResponse;
import com.dreamhomes.haven.domain.inspection.service.InspectionSlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inspection-slots")
@RequiredArgsConstructor
public class InspectionSlotController {

    private final InspectionSlotService slotService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionSlotResponse create(@Valid @RequestBody CreateSlotRequest req) {
        var s = slotService.create(req);
        return new InspectionSlotResponse(s.getId(), s.getListingId(), s.getAgentId(), s.getStartAt(), s.getEndAt());
    }

    @GetMapping("/{id}")
    public InspectionSlotResponse get(@PathVariable Long id) {
        var s = slotService.get(id);
        return new InspectionSlotResponse(s.getId(), s.getListingId(), s.getAgentId(), s.getStartAt(), s.getEndAt());
    }
}