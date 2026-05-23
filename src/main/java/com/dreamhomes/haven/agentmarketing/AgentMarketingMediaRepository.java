package com.dreamhomes.haven.agentmarketing;

import com.dreamhomes.haven.agentmarketing.model.AgentMarketingMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMarketingMediaRepository extends JpaRepository<AgentMarketingMedia, Long> {

    List<AgentMarketingMedia> findByUserIdOrderByDisplayOrderAscIdAsc(Long userId);

    long countByUserId(Long userId);
}
