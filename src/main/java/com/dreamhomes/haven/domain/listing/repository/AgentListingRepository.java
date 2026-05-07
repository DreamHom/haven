package com.dreamhomes.haven.domain.listing.repository;

import com.dreamhomes.haven.domain.listing.model.AgentListing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentListingRepository extends JpaRepository<AgentListing, Long> {
    List<AgentListing> findByAgentId(Long agentId);
    List<AgentListing> findByListingId(Long listingId);
}

