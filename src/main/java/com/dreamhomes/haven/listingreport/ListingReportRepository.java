package com.dreamhomes.haven.listingreport;

import com.dreamhomes.haven.listingreport.model.ListingReport;
import com.dreamhomes.haven.listingreport.model.ListingReportStatus;
import com.dreamhomes.haven.listingreport.model.ReportReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {

    /**
     * Backs the application-side duplicate check before insert. The unique index
     * {@code listing_reports_one_per_user_per_listing} is the safety net behind it —
     * a TOCTOU race still resolves to 409 because the constraint violation surfaces.
     */
    boolean existsByListingIdAndReporterUserId(Long listingId, Long reporterUserId);

    /**
     * Backs {@code GET /api/admin/listing-reports}. All filters optional; PENDING-only
     * by default in the admin queue but every state is queryable. Persona audit
     * (Dayo) flagged this as a critical-shape gap matching the audit-log one.
     */
    @Query("""
            SELECT r FROM ListingReport r
             WHERE (:status IS NULL OR r.status = :status)
               AND (:reason IS NULL OR r.reason = :reason)
               AND (:listingId IS NULL OR r.listingId = :listingId)
               AND (:reporterUserId IS NULL OR r.reporterUserId = :reporterUserId)
             ORDER BY r.createdAt DESC
            """)
    Page<ListingReport> search(@Param("status") ListingReportStatus status,
                               @Param("reason") ReportReason reason,
                               @Param("listingId") Long listingId,
                               @Param("reporterUserId") Long reporterUserId,
                               Pageable pageable);

    /** Backs the upcoming {@code GET /api/listings/{id}/reports/mine}. */
    Page<ListingReport> findByReporterUserIdOrderByCreatedAtDesc(Long reporterUserId, Pageable pageable);

    /**
     * Trust-signal pill on {@code GET /api/listings/{id}} — non-zero means the listing
     * has open complaints worth surfacing. Persona audit (Ngozi) flagged this absent.
     */
    long countByListingIdAndStatus(Long listingId, ListingReportStatus status);
}
