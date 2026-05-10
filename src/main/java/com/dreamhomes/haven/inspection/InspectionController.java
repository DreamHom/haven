package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('APPLICANT')")
    public InspectionResponse request(@AuthenticationPrincipal JwtPrincipal principal,
                                      @Valid @RequestBody RequestInspectionRequest body) {
        InspectionRequest saved = inspectionService.requestSlot(principal.userId(),
                new RequestInspectionCommand(body.slotId(), body.notes()));
        return new InspectionResponse(saved.getId(), saved.getSlotId(), saved.getApplicantId(),
                saved.getStatus(), saved.getNotes(), saved.getCreatedAt(), saved.getUpdatedAt());
    }
}
