package com.dreamhomes.haven.listingreport.service;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listingreport.ListingReportRepository;
import com.dreamhomes.haven.listingreport.dto.ReportListingCommand;
import com.dreamhomes.haven.listingreport.exception.DuplicateListingReportException;
import com.dreamhomes.haven.listingreport.model.ListingReport;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records user-submitted reports against listings and fans out a {@code LISTING_REPORTED}
 * notification to every admin so the moderation queue surfaces fresh reports without
 * the dashboard having to poll.
 *
 * <p>Constraints enforced here:</p>
 * <ul>
 *   <li>Listing must exist — missing → 404 (delegated to {@link ListingService#findById}).</li>
 *   <li>One report per user per listing — duplicate → 409. The DB unique index
 *       {@code listing_reports_one_per_user_per_listing} is the safety net behind the
 *       application-side {@code existsBy...} check.</li>
 * </ul>
 *
 * <p>Self-reports (owner reports their own listing) are technically valid — owners
 * sometimes want to flag their own row when scammers hijack it — so we don't filter
 * them out here.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ListingReportService {

    private final ListingReportRepository listingReportRepository;
    private final ListingService listingService;
    private final UserRepository userRepository;
    private final NotificationApi notificationApi;

    @Transactional
    public ListingReport report(Long reporterUserId, Long listingId, ReportListingCommand cmd) {
        // findById throws ListingNotFoundException → 404 if the listing is gone or never existed.
        listingService.findById(listingId);

        if (listingReportRepository.existsByListingIdAndReporterUserId(listingId, reporterUserId)) {
            throw new DuplicateListingReportException(listingId);
        }

        ListingReport saved = listingReportRepository.save(ListingReport.builder()
                .listingId(listingId)
                .reporterUserId(reporterUserId)
                .reason(cmd.reason())
                .details(cmd.details() == null ? null : cmd.details().trim())
                .build());

        fanOutToAdmins(saved);

        log.info("Recorded listingReportId={} listingId={} reporterUserId={} reason={}",
                saved.getId(), listingId, reporterUserId, saved.getReason());
        return saved;
    }

    /**
     * Admin queue read — {@code GET /api/admin/listing-reports}. Persona audit (Dayo)
     * flagged that user-filed reports were going into a black box: write-only moderation.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse> adminList(
            com.dreamhomes.haven.listingreport.model.ListingReportStatus status,
            com.dreamhomes.haven.listingreport.model.ReportReason reason,
            Long listingId, Long reporterUserId,
            org.springframework.data.domain.Pageable pageable) {
        return listingReportRepository.search(status, reason, listingId, reporterUserId, pageable)
                .map(this::toAdminResponse);
    }

    /** Reporter's own filings — backs {@code GET /api/listings/reports/mine}. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse> listMine(
            Long reporterUserId, org.springframework.data.domain.Pageable pageable) {
        return listingReportRepository.findByReporterUserIdOrderByCreatedAtDesc(reporterUserId, pageable)
                .map(this::toAdminResponse);
    }

    /** Admin marks a report RESOLVED (acted on) with a note for the audit trail. */
    @Transactional
    public com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse resolve(
            Long adminId, Long reportId, String note) {
        return decide(adminId, reportId, note, com.dreamhomes.haven.listingreport.model.ListingReportStatus.RESOLVED);
    }

    /** Admin marks a report DISMISSED (not actionable) with a note. */
    @Transactional
    public com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse dismiss(
            Long adminId, Long reportId, String note) {
        return decide(adminId, reportId, note, com.dreamhomes.haven.listingreport.model.ListingReportStatus.DISMISSED);
    }

    private com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse decide(
            Long adminId, Long reportId, String note,
            com.dreamhomes.haven.listingreport.model.ListingReportStatus newStatus) {
        ListingReport report = listingReportRepository.findById(reportId)
                .orElseThrow(() -> new com.dreamhomes.haven.listingreport.exception.ListingReportNotFoundException(reportId));
        if (report.getStatus() != com.dreamhomes.haven.listingreport.model.ListingReportStatus.PENDING) {
            throw new com.dreamhomes.haven.listingreport.exception.ListingReportAlreadyResolvedException(reportId);
        }
        report.setStatus(newStatus);
        report.setResolutionNote(note);
        report.setResolvedByAdminId(adminId);
        report.setResolvedAt(java.time.Instant.now());
        ListingReport saved = listingReportRepository.save(report);

        // Notify the original reporter that their report was actioned.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", saved.getId());
        payload.put("listingId", saved.getListingId());
        payload.put("status", saved.getStatus().name());
        payload.put("note", note);
        notificationApi.recordSync(NotificationKind.LISTING_REPORT_RESOLVED, saved.getReporterUserId(), payload);

        log.info("Admin {} {} reportId={} listingId={} note='{}'",
                adminId, newStatus, reportId, saved.getListingId(), note);
        return toAdminResponse(saved);
    }

    private com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse toAdminResponse(ListingReport r) {
        return new com.dreamhomes.haven.listingreport.dto.AdminListingReportResponse(
                r.getId(), r.getListingId(), r.getReporterUserId(), r.getReason(), r.getDetails(),
                r.getStatus(), r.getResolutionNote(), r.getResolvedByAdminId(), r.getResolvedAt(),
                r.getCreatedAt());
    }

    private void fanOutToAdmins(ListingReport report) {
        List<Long> adminIds = userRepository.findIdsByRole(Role.ADMIN);
        if (adminIds.isEmpty()) {
            // No admins seeded yet — log and move on. The report row still exists for
            // when one shows up. (Should never happen in any non-broken deployment because
            // V11 seeds an admin at migration time.)
            log.warn("No ADMIN users to notify for listingReportId={}", report.getId());
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.getId());
        payload.put("listingId", report.getListingId());
        payload.put("reporterUserId", report.getReporterUserId());
        payload.put("reason", report.getReason().name());
        for (Long adminId : adminIds) {
            notificationApi.recordSync(NotificationKind.LISTING_REPORTED, adminId, payload);
        }
    }
}
