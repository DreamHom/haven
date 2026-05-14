package com.dreamhomes.haven.ad;

import com.dreamhomes.haven.ad.model.AdCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdCampaignRepository extends JpaRepository<AdCampaign, Long> {

    Page<AdCampaign> findBySponsorUserIdOrderByCreatedAtDesc(Long sponsorUserId, Pageable pageable);

    Page<AdCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<AdCampaign> findByIdAndSponsorUserId(Long id, Long sponsorUserId);
}
