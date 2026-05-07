package com.dreamhomes.haven.agentlisting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Owner-to-agent assignment for a single listing. The owner is the one who initiates
 * the request (the {@code requestedByOwnerId} is denormalised from the listing's owner
 * for fast filtering on "my outstanding invites" without joining listings).
 *
 * <p>Two partial UQ indexes from V13 enforce the invariants:
 * <ul>
 *   <li>At most one row per listing in {@link AgentListingStatus#REQUESTED} state.</li>
 *   <li>At most one row per listing in {@link AgentListingStatus#ACCEPTED} state.</li>
 * </ul>
 * Terminal rows ({@code DECLINED}, {@code REVOKED}) fall out of both indexes, preserving
 * the audit history while leaving room for fresh invites.
 */
@Entity
@Table(name = "agent_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "agent_user_id", nullable = false)
    private Long agentUserId;

    /** Denormalised from the listing's owner at request time — read-side speed without a join. */
    @Column(name = "requested_by_owner_id", nullable = false)
    private Long requestedByOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private AgentListingStatus status = AgentListingStatus.REQUESTED;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Optimistic lock — guards owner-revoke racing agent-accept on the same row. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
