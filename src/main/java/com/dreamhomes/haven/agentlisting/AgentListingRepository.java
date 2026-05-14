package com.dreamhomes.haven.agentlisting;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dreamhomes.haven.agentlisting.model.AgentListing;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;

public interface AgentListingRepository extends JpaRepository<AgentListing, Long> {

    /**
     * Pre-flight checks the service runs to short-circuit duplicate-pending and
     * already-active rows with a clean domain exception. The partial UQ indexes from
     * V13 are the actual guarantee — these just avoid hitting the constraint when we can.
     */
    boolean existsByListingIdAndStatus(Long listingId, AgentListingStatus status);

    /** Backs {@code GET /api/agent-listings/mine} for an authenticated agent. */
    Page<AgentListing> findByAgentUserIdOrderByRequestedAtDesc(Long agentUserId, Pageable pageable);

    /** Backs {@code GET /api/agent-listings/mine} for an authenticated owner. */
    Page<AgentListing> findByRequestedByOwnerIdOrderByRequestedAtDesc(Long ownerId, Pageable pageable);

    /** Status-filtered variants for {@code ?status=} on the agent + owner /mine views. */
    Page<AgentListing> findByAgentUserIdAndStatusOrderByRequestedAtDesc(
            Long agentUserId, AgentListingStatus status, Pageable pageable);

    Page<AgentListing> findByRequestedByOwnerIdAndStatusOrderByRequestedAtDesc(
            Long ownerId, AgentListingStatus status, Pageable pageable);

    /**
     * The single ACCEPTED row for a listing, if any. Backs the {@code assignedAgentId}
     * trust-signal field on {@code GET /api/listings/{id}}. The unique partial index
     * from V13 guarantees at most one ACCEPTED per listing.
     */
    Optional<AgentListing> findFirstByListingIdAndStatus(Long listingId, AgentListingStatus status);

    boolean existsByListingIdAndAgentUserIdAndStatus(Long listingId, Long agentUserId, AgentListingStatus status);
}
