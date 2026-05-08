package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.listing.ListingApi;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InspectionSlotService {

    private final InspectionSlotRepository slotRepository;
    private final ListingApi listingApi;

    @Transactional
    public InspectionSlot create(Long callerId, Long listingId, CreateSlotCommand cmd) {
        if (!cmd.endsAt().isAfter(cmd.startsAt())) {
            throw new InvalidSlotWindowException();
        }
        Long ownerId = listingApi.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (!ownerId.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        InspectionSlot saved;
        try {
            // saveAndFlush instead of save: forces the INSERT and the EXCLUDE check to
            // run inside this method so we can catch and translate cleanly.
            saved = slotRepository.saveAndFlush(InspectionSlot.builder()
                    .listingId(listingId)
                    .startsAt(cmd.startsAt())
                    .endsAt(cmd.endsAt())
                    .createdAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException overlap) {
            // Only the GiST EXCLUDE constraint can fire here.
            throw new SlotOverlapException();
        }
        log.info("Created inspectionSlotId={} listingId={} ownerId={} startsAt={}",
                saved.getId(), listingId, callerId, saved.getStartsAt());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InspectionSlot> listAvailableForListing(Long listingId) {
        return slotRepository.findAvailableForListing(listingId);
    }
}
