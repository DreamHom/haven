package com.dreamhomes.haven.listingreport;

import com.dreamhomes.haven.listingreport.model.ListingReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    /**
     * Backs the application-side duplicate check before insert. The unique index
     * {@code listing_reports_one_per_user_per_listing} is the safety net behind it —
     * a TOCTOU race still resolves to 409 because the constraint violation surfaces.
     */
    boolean existsByListingIdAndReporterUserId(Long listingId, Long reporterUserId);
}
