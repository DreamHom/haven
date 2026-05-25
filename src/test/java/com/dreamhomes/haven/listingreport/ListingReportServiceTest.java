package com.dreamhomes.haven.listingreport;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.listingreport.dto.ReportListingCommand;
import com.dreamhomes.haven.listingreport.exception.DuplicateListingReportException;
import com.dreamhomes.haven.listingreport.model.ListingReport;
import com.dreamhomes.haven.listingreport.model.ReportReason;
import com.dreamhomes.haven.listingreport.service.ListingReportService;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer behaviour for {@link ListingReportService}:
 *
 * <ul>
 *   <li>404 surfaces from {@code ListingService.findById} before we touch our own state.</li>
 *   <li>Duplicate per-user-per-listing → 409 without inserting a second row.</li>
 *   <li>On success: one row written, one notification per admin, with the right payload.</li>
 *   <li>With zero admins seeded the row still saves (notifications are a side channel,
 *       not a precondition).</li>
 * </ul>
 *
 * <p>DB-side enforcement of the unique index is verified by the IT, not here.</p>
 */
@ExtendWith(MockitoExtension.class)
class ListingReportServiceTest {

    @Mock ListingReportRepository listingReportRepository;
    @Mock ListingService listingService;
    @Mock UserRepository userRepository;
    @Mock NotificationApi notificationApi;

    ListingReportService service;

    @BeforeEach
    void setUp() {
        service = new ListingReportService(listingReportRepository, listingService,
                userRepository, notificationApi);
    }

    @Test
    void reportingMissingListingSurfaces404FromListingService() {
        when(listingService.findById(404L)).thenThrow(new ListingNotFoundException(404L));

        assertThatThrownBy(() -> service.report(7L, 404L,
                new ReportListingCommand(ReportReason.SCAM, "fake")))
                .isInstanceOf(ListingNotFoundException.class);

        // Critically: we never reached our own state, so no duplicate-check, save, or fan-out.
        verify(listingReportRepository, never()).existsByListingIdAndReporterUserId(any(), any());
        verify(listingReportRepository, never()).save(any());
        verify(notificationApi, never()).recordSync(any(), any(), any());
    }

    @Test
    void duplicateReportFromSameUserOnSameListingThrows409() {
        when(listingService.findById(42L)).thenReturn(stubListing(42L, 100L));
        when(listingReportRepository.existsByListingIdAndReporterUserId(42L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.report(7L, 42L,
                new ReportListingCommand(ReportReason.SCAM, null)))
                .isInstanceOf(DuplicateListingReportException.class);

        verify(listingReportRepository, never()).save(any());
        verify(notificationApi, never()).recordSync(any(), any(), any());
    }

    @Test
    void successfulReportPersistsRowAndFansOutOneNotificationPerAdmin() {
        when(listingService.findById(42L)).thenReturn(stubListing(42L, 100L));
        when(listingReportRepository.existsByListingIdAndReporterUserId(42L, 7L)).thenReturn(false);
        when(listingReportRepository.save(any(ListingReport.class)))
                .thenAnswer(inv -> {
                    ListingReport r = inv.getArgument(0);
                    r.setId(999L);
                    r.setCreatedAt(Instant.parse("2026-05-10T08:30:00Z"));
                    return r;
                });
        when(userRepository.findIdsByRole(Role.ADMIN)).thenReturn(List.of(1L, 2L, 3L));

        ListingReport saved = service.report(7L, 42L,
                new ReportListingCommand(ReportReason.OFF_PLATFORM_FEES, "  asked for ₦200k  "));

        ArgumentCaptor<ListingReport> rowCaptor = ArgumentCaptor.forClass(ListingReport.class);
        verify(listingReportRepository).save(rowCaptor.capture());
        ListingReport written = rowCaptor.getValue();
        assertThat(written.getListingId()).isEqualTo(42L);
        assertThat(written.getReporterUserId()).isEqualTo(7L);
        assertThat(written.getReason()).isEqualTo(ReportReason.OFF_PLATFORM_FEES);
        // Service trims leading/trailing whitespace on details.
        assertThat(written.getDetails()).isEqualTo("asked for ₦200k");

        ArgumentCaptor<Long> recipientCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationApi, times(3))
                .recordSync(eq(NotificationKind.LISTING_REPORTED),
                        recipientCaptor.capture(), payloadCaptor.capture());

        assertThat(recipientCaptor.getAllValues()).containsExactly(1L, 2L, 3L);
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("reportId")).isEqualTo(999L);
        assertThat(payload.get("listingId")).isEqualTo(42L);
        assertThat(payload.get("reporterUserId")).isEqualTo(7L);
        assertThat(payload.get("reason")).isEqualTo("OFF_PLATFORM_FEES");

        assertThat(saved.getId()).isEqualTo(999L);
    }

    @Test
    void zeroAdminsStillSavesTheRowAndLogsAWarning() {
        when(listingService.findById(42L)).thenReturn(stubListing(42L, 100L));
        when(listingReportRepository.existsByListingIdAndReporterUserId(42L, 7L)).thenReturn(false);
        when(listingReportRepository.save(any(ListingReport.class)))
                .thenAnswer(inv -> {
                    ListingReport r = inv.getArgument(0);
                    r.setId(1L);
                    return r;
                });
        when(userRepository.findIdsByRole(Role.ADMIN)).thenReturn(List.of());

        service.report(7L, 42L, new ReportListingCommand(ReportReason.OTHER, null));

        verify(listingReportRepository).save(any());
        verify(notificationApi, never()).recordSync(any(), any(), any());
    }

    private static ListingResponse stubListing(Long id, Long ownerId) {
        return new ListingResponse(id, 1L, ownerId, ListingType.SALE,
                new BigDecimal("1000000"), "NGN",
                null, null, null,
                null, null, null, null,
                null, false,
                ListingStatus.LIVE, null, 0L,
                Instant.now(), Instant.now(), null, null, null, null, null, null, null, null);
    }
}
