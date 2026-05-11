package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import com.dreamhomes.haven.property.dto.PropertySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.dreamhomes.haven.listing.dto.CreateListingCommand;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.dto.UpdateListingCommand;
import com.dreamhomes.haven.listing.exception.InvalidListingTransitionException;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;

/**
 * Implementation of {@link ListingService}. Cross-aggregate property reads go through
 * {@link PropertyService} only — this module never imports {@code property-impl}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ListingService {

    private static final String DEFAULT_CURRENCY = "NGN";

    private final ListingRepository listingRepository;
    private final PropertyService propertyService;
    private final ListingMapper listingMapper;

    @Transactional
    public Listing create(Long callerId, CreateListingCommand cmd) {
        Long propertyOwner = propertyService.ownerOf(cmd.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(cmd.propertyId()));
        if (!propertyOwner.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        Listing saved = listingRepository.save(Listing.builder()
                .propertyId(cmd.propertyId())
                .ownerId(callerId)
                .listingType(cmd.listingType())
                .askingPrice(cmd.askingPrice())
                .currency(cmd.currency() != null ? cmd.currency() : DEFAULT_CURRENCY)
                .cautionFee(cmd.cautionFee())
                .serviceCharge(cmd.serviceCharge())
                .agencyFee(cmd.agencyFee())
                .status(ListingStatus.LIVE)
                .build());
        log.info("Created listingId={} propertyId={} ownerId={} type={}",
                saved.getId(), saved.getPropertyId(), callerId, saved.getListingType());
        return saved;
    }

    /**
     * Returns LIVE listings paired with their parent property summary. Two queries total
     * (listings page + bulk property summary fetch by id), not N+1.
     */
    @Transactional(readOnly = true)
    public Page<ListingWithProperty> browsePublic(Pageable pageable) {
        return withSummaries(listingRepository.findByStatus(ListingStatus.LIVE, pageable));
    }

    /**
     * Owner's portfolio. Backs {@code GET /api/listings/mine} — the read-side
     * Amaka and Biodun flagged as missing in the persona audit. Returns every
     * listing they own (LIVE, PAUSED, CLOSED, TAKEN_DOWN), newest first.
     */
    @Transactional(readOnly = true)
    public Page<ListingWithProperty> listMine(Long ownerId, Pageable pageable) {
        return withSummaries(listingRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable));
    }

    private Page<ListingWithProperty> withSummaries(Page<Listing> listings) {
        if (listings.isEmpty()) {
            return listings.map(l -> new ListingWithProperty(l, null));
        }
        Set<Long> propertyIds = listings.stream()
                .map(Listing::getPropertyId)
                .collect(Collectors.toSet());
        Map<Long, PropertySummary> summaries = propertyService.findSummariesByIds(propertyIds);
        return listings.map(l -> new ListingWithProperty(l, summaries.get(l.getPropertyId())));
    }

    /**
     * Public detail. Bumps {@code view_count} via an atomic UPDATE — lock-free, so it
     * doesn't churn the @Version on every page view.
     */
    @Transactional
    public ListingWithProperty findPubliclyVisible(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        // Only LIVE listings are reachable to anonymous callers — paused/closed look 404
        // to the public to avoid leaking that they ever existed.
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new ListingNotFoundException(listingId);
        }
        PropertySummary property = propertyService.findSummary(listing.getPropertyId())
                .orElseThrow(() -> new PropertyNotFoundException(listing.getPropertyId()));
        listingRepository.incrementViewCount(listingId);
        return new ListingWithProperty(listing, property);
    }

    @Transactional
    public Listing update(Long callerId, Long listingId, UpdateListingCommand cmd) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (!listing.getOwnerId().equals(callerId)) {
            throw new NotPropertyOwnerException();
        }
        if (cmd.status() != null && !isAllowedTransition(listing.getStatus(), cmd.status())) {
            throw new InvalidListingTransitionException(listing.getStatus(), cmd.status());
        }
        if (cmd.askingPrice() != null) {
            listing.setAskingPrice(cmd.askingPrice());
        }
        if (cmd.status() != null) {
            listing.setStatus(cmd.status());
        }
        // updatedAt is bumped by JPA auditing on save (entity has @LastModifiedDate).
        Listing saved = listingRepository.save(listing);
        log.info("Updated listingId={} ownerId={} status={} price={}",
                saved.getId(), callerId, saved.getStatus(), saved.getAskingPrice());
        return saved;
    }

    /** CLOSED is terminal — no transitions out. Other transitions are free. */
    private static boolean isAllowedTransition(ListingStatus from, ListingStatus to) {
        return from != ListingStatus.CLOSED;
    }

    // ============================ ListingService ============================

    @Transactional(readOnly = true)
    public ListingResponse findById(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        PropertySummary summary = propertyService.findSummary(listing.getPropertyId()).orElse(null);
        return listingMapper.toResponse(listing, summary);
    }

    @Transactional(readOnly = true)
    public Optional<Long> ownerOf(Long listingId) {
        return listingRepository.findById(listingId).map(Listing::getOwnerId);
    }

    @Transactional(readOnly = true)
    public Optional<ListingStatus> statusOf(Long listingId) {
        return listingRepository.findById(listingId).map(Listing::getStatus);
    }

    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long listingId, Long userId) {
        return ownerOf(listingId).map(o -> o.equals(userId)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean exists(Long listingId) {
        return listingRepository.existsById(listingId);
    }

    @Transactional
    public void markApproved(Long listingId, Instant when) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        listing.setApprovedAt(when);
        listing.setUpdatedAt(when);
        listingRepository.save(listing);
    }

    @Transactional
    public void forceStatus(Long listingId, ListingStatus status, Instant when) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        listing.setStatus(status);
        listing.setUpdatedAt(when);
        listingRepository.save(listing);
    }

}
