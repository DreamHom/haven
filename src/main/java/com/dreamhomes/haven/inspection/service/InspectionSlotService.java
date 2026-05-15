package com.dreamhomes.haven.inspection.service;

import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.user.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.dreamhomes.haven.inspection.dto.CreateSlotCommand;
import com.dreamhomes.haven.inspection.exception.InvalidSlotWindowException;
import com.dreamhomes.haven.inspection.exception.SlotOverlapException;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.inspection.repository.InspectionSlotRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class InspectionSlotService {

    private final InspectionSlotRepository slotRepository;
    private final ListingService listingService;
    private final AgentListingRepository agentListingRepository;

    @Transactional
    public InspectionSlot create(Long callerId, Role role, Long listingId, CreateSlotCommand cmd) {
        if (!cmd.endsAt().isAfter(cmd.startsAt())) {
            throw new InvalidSlotWindowException();
        }
        Long ownerId = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        boolean allowed = callerId.equals(ownerId)
                || (role == Role.AGENT && agentListingRepository.existsByListingIdAndAgentUserIdAndStatus(
                        listingId, callerId, AgentListingStatus.ACCEPTED));
        if (!allowed) {
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
        // 404 if the listing is missing — see B-2 in the persona audit.
        if (!listingService.exists(listingId)) {
            throw new com.dreamhomes.haven.listing.exception.ListingNotFoundException(listingId);
        }
        return slotRepository.findAvailableForListing(listingId);
    }
}
