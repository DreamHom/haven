package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.NotPropertyOwnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        InspectionSlot saved = slotRepository.save(InspectionSlot.builder()
                .listingId(listingId)
                .startsAt(cmd.startsAt())
                .endsAt(cmd.endsAt())
                .createdAt(Instant.now())
                .build());
        log.info("Created inspectionSlotId={} listingId={} ownerId={} startsAt={}",
                saved.getId(), listingId, callerId, saved.getStartsAt());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InspectionSlot> listAvailableForListing(Long listingId) {
        return slotRepository.findAvailableForListing(listingId);
    }
}
