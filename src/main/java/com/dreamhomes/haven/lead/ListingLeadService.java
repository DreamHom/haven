package com.dreamhomes.haven.lead;

import com.dreamhomes.haven.lead.dto.CreateListingLeadRequest;
import com.dreamhomes.haven.lead.dto.ListingLeadResponse;
import com.dreamhomes.haven.lead.exception.ListingLeadConflictException;
import com.dreamhomes.haven.lead.exception.ListingLeadForbiddenException;
import com.dreamhomes.haven.lead.exception.ListingLeadNotFoundException;
import com.dreamhomes.haven.lead.exception.ListingNotAcceptingLeadsException;
import com.dreamhomes.haven.lead.model.ListingLead;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ListingLeadService {

    private final ListingLeadRepository listingLeadRepository;
    private final ListingService listingService;
    private final NotificationApi notificationApi;

    @Transactional
    public ListingLeadResponse submit(Long listingId, Long applicantUserId, CreateListingLeadRequest req) {
        ListingResponse listing = listingService.findById(listingId);
        if (listing.status() != ListingStatus.LIVE) {
            throw new ListingNotAcceptingLeadsException();
        }
        if (listing.ownerId().equals(applicantUserId)) {
            throw new ListingLeadForbiddenException();
        }
        if (listingLeadRepository.existsByListingIdAndApplicantUserId(listingId, applicantUserId)) {
            throw new ListingLeadConflictException();
        }
        ListingLead saved = listingLeadRepository.save(ListingLead.builder()
                .listingId(listingId)
                .applicantUserId(applicantUserId)
                .message(req.message())
                .contactPhone(req.contactPhone())
                .contactEmail(req.contactEmail())
                .build());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listingId", listingId);
        payload.put("leadId", saved.getId());
        notificationApi.recordSync(NotificationKind.LISTING_LEAD_SUBMITTED, listing.ownerId(), payload);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ListingLeadResponse> listForListingOwner(Long listingId, Long ownerUserId, Pageable pageable) {
        if (!listingService.isOwnedBy(listingId, ownerUserId)) {
            throw new NotPropertyOwnerException();
        }
        return listingLeadRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public ListingLeadResponse reveal(Long listingId, Long leadId, Long ownerUserId) {
        if (!listingService.isOwnedBy(listingId, ownerUserId)) {
            throw new NotPropertyOwnerException();
        }
        ListingLead lead = listingLeadRepository.findById(leadId)
                .orElseThrow(() -> new ListingLeadNotFoundException(leadId));
        if (!lead.getListingId().equals(listingId)) {
            throw new ListingLeadNotFoundException(leadId);
        }
        if (lead.getRevealedAt() == null) {
            lead.setRevealedAt(Instant.now());
            lead = listingLeadRepository.save(lead);
        }
        return toResponse(lead);
    }

    private ListingLeadResponse toResponse(ListingLead lead) {
        boolean revealed = lead.getRevealedAt() != null;
        return new ListingLeadResponse(
                lead.getId(),
                lead.getListingId(),
                lead.getApplicantUserId(),
                lead.getMessage(),
                lead.getCreatedAt(),
                revealed,
                revealed ? lead.getContactPhone() : null,
                revealed ? lead.getContactEmail() : null);
    }
}
