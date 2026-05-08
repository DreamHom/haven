package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.PropertyApi;
import com.dreamhomes.haven.property.PropertyNotFoundException;
import com.dreamhomes.haven.property.PropertySummary;
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

/**
 * Implementation of {@link ListingApi}. Cross-aggregate property reads go through
 * {@link PropertyApi} only — this module never imports {@code property-impl}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ListingService implements ListingApi {

    private static final String DEFAULT_CURRENCY = "NGN";

    private final ListingRepository listingRepository;
    private final PropertyApi propertyApi;

    @Transactional
    public Listing create(Long callerId, CreateListingCommand cmd) {
        Long propertyOwner = propertyApi.ownerOf(cmd.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(cmd.propertyId()));
        if (!propertyOwner.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        Instant now = Instant.now();
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
                .createdAt(now)
                .updatedAt(now)
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
        Page<Listing> listings = listingRepository.findByStatus(ListingStatus.LIVE, pageable);
        if (listings.isEmpty()) {
            return listings.map(l -> new ListingWithProperty(l, null));
        }
        Set<Long> propertyIds = listings.stream()
                .map(Listing::getPropertyId)
                .collect(Collectors.toSet());
        Map<Long, PropertySummary> summaries = propertyApi.findSummariesByIds(propertyIds);
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
        PropertySummary property = propertyApi.findSummary(listing.getPropertyId())
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
        listing.setUpdatedAt(Instant.now());
        Listing saved = listingRepository.save(listing);
        log.info("Updated listingId={} ownerId={} status={} price={}",
                saved.getId(), callerId, saved.getStatus(), saved.getAskingPrice());
        return saved;
    }

    /** CLOSED is terminal — no transitions out. Other transitions are free. */
    private static boolean isAllowedTransition(ListingStatus from, ListingStatus to) {
        return from != ListingStatus.CLOSED;
    }

    // ============================ ListingApi ============================

    @Override
    @Transactional(readOnly = true)
    public ListingResponse findById(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        PropertySummary summary = propertyApi.findSummary(listing.getPropertyId()).orElse(null);
        return toResponse(listing, summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> ownerOf(Long listingId) {
        return listingRepository.findById(listingId).map(Listing::getOwnerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ListingStatus> statusOf(Long listingId) {
        return listingRepository.findById(listingId).map(Listing::getStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long listingId, Long userId) {
        return ownerOf(listingId).map(o -> o.equals(userId)).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long listingId) {
        return listingRepository.existsById(listingId);
    }

    @Override
    @Transactional
    public void markApproved(Long listingId, Instant when) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        listing.setApprovedAt(when);
        listing.setUpdatedAt(when);
        listingRepository.save(listing);
    }

    @Override
    @Transactional
    public void forceStatus(Long listingId, ListingStatus status, Instant when) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        listing.setStatus(status);
        listing.setUpdatedAt(when);
        listingRepository.save(listing);
    }

    /** Internal entity → DTO mapper; package-private so {@link ListingController} can reuse. */
    static ListingResponse toResponse(Listing l, PropertySummary p) {
        return new ListingResponse(
                l.getId(), l.getPropertyId(), l.getOwnerId(), l.getListingType(),
                l.getAskingPrice(), l.getCurrency(),
                l.getCautionFee(), l.getServiceCharge(), l.getAgencyFee(),
                l.getStatus(), l.getApprovedAt(), l.getViewCount(),
                l.getCreatedAt(), l.getUpdatedAt(), p);
    }
}
