package com.dreamhomes.haven.domain.inspection.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inspection_requests")
@Getter
@Setter
@NoArgsConstructor
public class InspectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long slotId;
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    private InspectionStatus status = InspectionStatus.PENDING;

    private Instant createdAt = Instant.now();
}

