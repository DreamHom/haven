package com.dreamhomes.haven.domain.verification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "verifications")
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long subjectUserId;
    private Long propertyId;
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    private VerificationType type;

    @Enumerated(EnumType.STRING)
    private VerificationStatus status = VerificationStatus.PENDING;

    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getSubjectUserId() {
        return subjectUserId;
    }

    public void setSubjectUserId(Long subjectUserId) {
        this.subjectUserId = subjectUserId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public VerificationType getType() {
        return type;
    }

    public void setType(VerificationType type) {
        this.type = type;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public void setStatus(VerificationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

