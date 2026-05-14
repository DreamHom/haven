package com.dreamhomes.haven.lead;

import com.dreamhomes.haven.lead.model.ListingLead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingLeadRepository extends JpaRepository<ListingLead, Long> {

    Page<ListingLead> findByListingIdOrderByCreatedAtDesc(Long listingId, Pageable pageable);

    boolean existsByListingIdAndApplicantUserId(Long listingId, Long applicantUserId);
}
