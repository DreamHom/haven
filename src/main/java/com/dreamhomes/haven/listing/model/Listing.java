package com.dreamhomes.haven.listing.model;


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

/**
 * The market expression of a property. {@code owner_id} is denormalised from
 * {@code properties.owner_id} so authorisation checks ("am I the owner of this
 * listing?") don't need a join — the service is responsible for keeping the two
 * in sync at write time.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 32)
    private ListingType listingType;

    @Column(name = "asking_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal askingPrice;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Column(name = "caution_fee", precision = 12, scale = 2)
    private BigDecimal cautionFee;

    @Column(name = "service_charge", precision = 12, scale = 2)
    private BigDecimal serviceCharge;

    @Column(name = "agency_fee", precision = 12, scale = 2)
    private BigDecimal agencyFee;

    // Marketing-copy fields (V27). Optional — pre-existing listings have null. Persona
    // audit (Amaka, Biodun) flagged the absence as the reason listings looked
    // identical to applicants regardless of owner effort.
    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String headline;

    /** Off-plan / handover date for developer launches (Biodun). */
    @Column(name = "handover_date")
    private java.time.LocalDate handoverDate;

    /** External virtual-tour link (Matterport, YouTube, etc.). */
    @Column(name = "virtual_tour_url", length = 2048)
    private String virtualTourUrl;

    /** Optional floor-plan PDF/image URL (pointer only, same contract as photos). */
    @Column(name = "floor_plan_url", length = 2048)
    private String floorPlanUrl;

    @Column(name = "price_negotiable", nullable = false)
    @Builder.Default
    private boolean priceNegotiable = false;

    /** Free-text pets policy (e.g. "Cats only", "No pets"). */
    @Column(name = "pets_allowed", length = 128)
    private String petsAllowed;

    @Column(name = "utilities_note", columnDefinition = "TEXT")
    private String utilitiesNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ListingStatus status = ListingStatus.LIVE;

    @CreatedDate

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Set when an admin approves the listing — grants the verified-listing badge per
     * PRD §4.1. Approval is non-blocking: listings go LIVE immediately and are visible
     * with or without this stamp.
     */
    @Column(name = "approved_at")
    private Instant approvedAt;

    /**
     * Aggregate counter — bumped atomically by a native UPDATE on every public detail
     * GET. We don't track per-user views (no row-per-anonymous-visitor explosion); the
     * atomic SQL increment is lock-free and bypasses Hibernate's optimistic version
     * check, so a popular listing's @Version doesn't churn on every page view.
     */
    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    /** Optimistic lock — JPA increments on every save, rejects stale-version writes. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
