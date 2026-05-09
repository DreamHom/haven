package com.dreamhomes.haven.domain.verification.controller;

import com.dreamhomes.haven.domain.verification.dto.ReviewVerificationRequest;
import com.dreamhomes.haven.domain.verification.dto.SubmitVerificationRequest;
import com.dreamhomes.haven.domain.verification.dto.VerificationResponse;
import com.dreamhomes.haven.domain.verification.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verifications")
@RequiredArgsConstructor
public class VerificationController {
    private final VerificationService verificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VerificationResponse submit(@Valid @RequestBody SubmitVerificationRequest req) {
        var v = verificationService.submit(req);
        return new VerificationResponse(v.getId(), v.getSubjectUserId(), v.getPropertyId(), v.getType(), v.getStatus(), v.getDocumentUrl(), v.getCreatedAt());
    }

    @PutMapping("/{id}/review")
    public VerificationResponse review(@PathVariable Long id, @Valid @RequestBody ReviewVerificationRequest req) {
        var v = verificationService.review(id, req);
        return new VerificationResponse(v.getId(), v.getSubjectUserId(), v.getPropertyId(), v.getType(), v.getStatus(), v.getDocumentUrl(), v.getCreatedAt());
    }

    @GetMapping("/{id}")
    public VerificationResponse get(@PathVariable Long id) {
        var v = verificationService.get(id);
        return new VerificationResponse(v.getId(), v.getSubjectUserId(), v.getPropertyId(), v.getType(), v.getStatus(), v.getDocumentUrl(), v.getCreatedAt());
    }
}