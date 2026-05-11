package com.dreamhomes.haven.inspection.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "inspection_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private InspectionRequestStatus status = InspectionRequestStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
