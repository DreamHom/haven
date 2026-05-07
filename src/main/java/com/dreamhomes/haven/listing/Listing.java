package com.dreamhomes.haven.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The market expression of a property. {@code owner_id} is denormalised from
 * {@code properties.owner_id} so authorisation checks ("am I the owner of this
 * listing?") don't need a join — the service is responsible for keeping the two
 * in sync at write time.
 */
@Entity
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ListingStatus status = ListingStatus.LIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
