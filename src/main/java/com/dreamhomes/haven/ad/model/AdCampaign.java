package com.dreamhomes.haven.ad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ad_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sponsor_user_id", nullable = false)
    private Long sponsorUserId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private AdCampaignStatus status = AdCampaignStatus.DRAFT;

    @Column(name = "budget_cents")
    private Long budgetCents;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
