package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
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
    private final ListingRepository listingRepository;

    @Transactional
    public InspectionSlot create(Long callerId, Long listingId, CreateSlotCommand cmd) {
        if (!cmd.endsAt().isAfter(cmd.startsAt())) {
            throw new InvalidSlotWindowException();
        }
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (!listing.getOwnerId().equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        InspectionSlot saved;
        try {
            // saveAndFlush instead of save: forces the INSERT and the EXCLUDE check to
            // run inside this method so we can catch and translate cleanly. With plain
            // save(), the violation would surface at TX-commit time — outside our reach.
            saved = slotRepository.saveAndFlush(InspectionSlot.builder()
                    .listingId(listingId)
                    .startsAt(cmd.startsAt())
                    .endsAt(cmd.endsAt())
                    .createdAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException overlap) {
            // Only the GiST EXCLUDE constraint can fire here — schema CHECKs are
            // upstream (validated in code) and there are no other constraints on
            // this table.
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
