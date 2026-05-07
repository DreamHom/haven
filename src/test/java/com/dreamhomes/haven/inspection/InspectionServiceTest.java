package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock InspectionSlotRepository slotRepository;
    @Mock InspectionRequestRepository requestRepository;
    @Mock ListingRepository listingRepository;
    @Mock InspectionEventPublisher eventPublisher;

    InspectionService service;

    @BeforeEach
    void setUp() {
        service = new InspectionService(slotRepository, requestRepository, listingRepository, eventPublisher);
    }

    @Test
    void persistsPendingRequestAndPublishesEventOnSuccessfulClaim() {
        Long applicantId = 100L;
        InspectionSlot slot = slotFor(50L, 7L);
        Listing listing = listingOwnedBy(7L, 99L);
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slot));
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listing));
        when(requestRepository.save(any(InspectionRequest.class))).thenAnswer(inv -> {
            InspectionRequest r = inv.getArgument(0);
            r.setId(1234L);
            return r;
        });

        InspectionRequest result = service.requestSlot(applicantId, new RequestInspectionCommand(50L, "I'm interested"));

        ArgumentCaptor<InspectionRequest> requestCap = ArgumentCaptor.forClass(InspectionRequest.class);
        verify(requestRepository).save(requestCap.capture());
        assertThat(requestCap.getValue().getStatus()).isEqualTo(InspectionRequestStatus.PENDING);
        assertThat(requestCap.getValue().getApplicantId()).isEqualTo(applicantId);
        assertThat(requestCap.getValue().getSlotId()).isEqualTo(50L);

        ArgumentCaptor<InspectionRequestedEvent> eventCap = ArgumentCaptor.forClass(InspectionRequestedEvent.class);
        verify(eventPublisher).publishInspectionRequested(eventCap.capture());
        InspectionRequestedEvent published = eventCap.getValue();
        assertThat(published.inspectionRequestId()).isEqualTo(1234L);
        assertThat(published.slotId()).isEqualTo(50L);
        assertThat(published.listingId()).isEqualTo(7L);
        assertThat(published.ownerId()).isEqualTo(99L);
        assertThat(published.applicantId()).isEqualTo(applicantId);
        assertThat(published.startsAt()).isEqualTo(slot.getStartsAt());

        assertThat(result.getId()).isEqualTo(1234L);
    }

    @Test
    void throwsWhenSlotDoesNotExistAndDoesNotPublish() {
        when(slotRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestSlot(100L, new RequestInspectionCommand(404L, null)))
                .isInstanceOf(SlotNotFoundException.class);

        verify(requestRepository, never()).save(any());
        verify(eventPublisher, never()).publishInspectionRequested(any());
    }

    @Test
    void translatesPartialUniqueViolationToSlotAlreadyClaimedAndDoesNotPublish() {
        // Two applicants race for the same slot. The DB partial unique index lets one
        // win; the loser's save throws DataIntegrityViolationException. We surface that
        // as a clean 409 — and we don't fire the Kafka event for the failed attempt.
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slotFor(50L, 7L)));
        when(listingRepository.findById(7L)).thenReturn(Optional.of(listingOwnedBy(7L, 99L)));
        when(requestRepository.save(any(InspectionRequest.class)))
                .thenThrow(new DataIntegrityViolationException("dup slot"));

        assertThatThrownBy(() -> service.requestSlot(100L, new RequestInspectionCommand(50L, null)))
                .isInstanceOf(SlotAlreadyClaimedException.class);

        verify(eventPublisher, never()).publishInspectionRequested(any());
    }

    @Test
    void throwsWhenSlotPointsAtAVanishedListing() {
        // Defence in depth — the FK from slot to listing means this shouldn't happen,
        // but if it does (manual SQL, future hard-delete) we want a clean 404 not an NPE.
        when(slotRepository.findById(50L)).thenReturn(Optional.of(slotFor(50L, 7L)));
        when(listingRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestSlot(100L, new RequestInspectionCommand(50L, null)))
                .isInstanceOf(ListingNotFoundException.class);
    }

    private static InspectionSlot slotFor(Long slotId, Long listingId) {
        return InspectionSlot.builder()
                .id(slotId).listingId(listingId)
                .startsAt(Instant.parse("2026-06-01T10:00:00Z"))
                .endsAt(Instant.parse("2026-06-01T11:00:00Z"))
                .createdAt(Instant.now()).build();
    }

    private static Listing listingOwnedBy(Long listingId, Long ownerId) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(listingId).propertyId(1L).ownerId(ownerId)
                .listingType(ListingType.RENT).askingPrice(new BigDecimal("100.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(now).updatedAt(now).build();
    }
}
