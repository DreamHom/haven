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
