package com.dreamhomes.haven.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * External video URL for a listing (walk-through, drone reel, etc.). Same pointer-only
 * contract as {@link ListingPhoto} — no bytes in Postgres.
 */
@Entity
@Table(name = "listing_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(length = 255)
    private String caption;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
}
