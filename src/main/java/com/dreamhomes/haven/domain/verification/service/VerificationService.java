package com.dreamhomes.haven.domain.verification.service;

import com.dreamhomes.haven.domain.verification.dto.ReviewVerificationRequest;
import com.dreamhomes.haven.domain.verification.dto.SubmitVerificationRequest;
import com.dreamhomes.haven.domain.verification.model.Verification;
import com.dreamhomes.haven.domain.verification.repository.VerificationRepository;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private final VerificationRepository verificationRepository;

    @Transactional
    public Verification submit(SubmitVerificationRequest req) {
        var v = new Verification();
        v.setSubjectUserId(req.subjectUserId());
        v.setPropertyId(req.propertyId());
        v.setType(req.type());
        v.setDocumentUrl(req.documentUrl());
        return verificationRepository.save(v);
    }

    @Transactional
    public Verification review(Long id, ReviewVerificationRequest req) {
        var v = verificationRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Verification not found"));
        
        v.setStatus(req.status());
        return verificationRepository.save(v);
    }

    @Transactional(readOnly = true)
    public Verification get(Long id) {
        return verificationRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Verification not found"));
    }
}

