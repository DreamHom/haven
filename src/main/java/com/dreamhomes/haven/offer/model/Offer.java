package com.dreamhomes.haven.offer.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
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
@Table(name = "offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * Applicant-declared intent: RENT / BUY / RENT_TO_BUY. Optional; null = unspecified.
     * Persona audit (Ngozi) — rent-to-buy needs to be a first-class field, not a hope
     * buried in {@code message}.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private OfferIntent intent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private OfferStatus status = OfferStatus.PENDING;

    /**
     * Counter-offer chain (Phase 13). The original offer has {@code parent_offer_id = null};
     * each counter points back to the prior row. The whole sequence reads as an
     * immutable history.
     */
    @Column(name = "parent_offer_id")
    private Long parentOfferId;

    /**
     * Who proposed THIS specific row. Original offer: applicant. Owner counter:
     * owner. Applicant counter to that: applicant. The "other party" is the only one
     * authorised to act on the row — accept, decline, or counter back.
     */
    @Column(name = "proposed_by_user_id", nullable = false)
    private Long proposedByUserId;

    @CreatedDate

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock — concurrent owner/admin/automation writes get rejected, not silently lost. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
