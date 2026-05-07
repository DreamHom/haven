package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.inspection.events.InspectionRequestedEvent;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionSlotRepository slotRepository;
    private final InspectionRequestRepository requestRepository;
    private final ListingRepository listingRepository;
    private final InspectionEventPublisher eventPublisher;

    @Transactional
    public InspectionRequest requestSlot(Long applicantId, RequestInspectionCommand cmd) {
        InspectionSlot slot = slotRepository.findById(cmd.slotId())
                .orElseThrow(() -> new SlotNotFoundException(cmd.slotId()));
        Listing listing = listingRepository.findById(slot.getListingId())
                .orElseThrow(() -> new ListingNotFoundException(slot.getListingId()));

        Instant now = Instant.now();
        InspectionRequest request = InspectionRequest.builder()
                .slotId(slot.getId())
                .applicantId(applicantId)
                .status(InspectionRequestStatus.PENDING)
                .notes(cmd.notes())
                .createdAt(now)
                .updatedAt(now)
                .build();

        InspectionRequest saved;
        try {
            saved = requestRepository.save(request);
        } catch (DataIntegrityViolationException raceWithAnotherApplicant) {
            // The partial unique index already blocked the duplicate active claim.
            // Translate to a 409 so the API speaks the same shape every other duplicate
            // path uses.
            throw new SlotAlreadyClaimedException();
        }

        eventPublisher.publishInspectionRequested(new InspectionRequestedEvent(
                saved.getId(),
                saved.getSlotId(),
                listing.getId(),
                listing.getOwnerId(),
                applicantId,
                slot.getStartsAt(),
                slot.getEndsAt(),
                now));
        log.info("Created inspectionRequestId={} slotId={} applicantId={}",
                saved.getId(), saved.getSlotId(), applicantId);
        return saved;
    }
}
