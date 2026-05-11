package com.dreamhomes.haven.review;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Post-deal review. Created after the listing is CLOSED and the reviewer was a
 * participant in the deal (owner of the listing OR applicant on an ACCEPTED offer).
 * Reviews are immutable in Phase 10 — edit/admin-takedown defer to a follow-on phase.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "listing_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "reviewer_user_id", nullable = false)
    private Long reviewerUserId;

    @Column(name = "reviewee_user_id", nullable = false)
    private Long revieweeUserId;

    /** 1..5 inclusive — enforced by both DTO @Min/@Max and DB CHECK. */
    @Column(nullable = false)
    private Short rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Soft-delete: paired with {@link #deletedByUserId} via DB CHECK constraint. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_user_id")
    private Long deletedByUserId;

    @Column(name = "deletion_reason", columnDefinition = "TEXT")
    private String deletionReason;

    @CreatedDate

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
