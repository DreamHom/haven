package com.dreamhomes.haven.domain.inspection.controller;

import com.dreamhomes.haven.domain.inspection.dto.BookInspectionRequest;
import com.dreamhomes.haven.domain.inspection.dto.InspectionRequestResponse;
import com.dreamhomes.haven.domain.inspection.service.InspectionRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inspection-requests")
@RequiredArgsConstructor
public class InspectionRequestController {

    private final InspectionRequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionRequestResponse book(@Valid @RequestBody BookInspectionRequest req) {
        var r = requestService.book(req);
        return new InspectionRequestResponse(r.getId(), r.getSlotId(), r.getApplicantId(), r.getStatus(), r.getCreatedAt());
    }
}

