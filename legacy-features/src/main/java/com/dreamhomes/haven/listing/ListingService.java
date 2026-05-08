package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyNotFoundException;
import com.dreamhomes.haven.property.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ListingService {

    private static final String DEFAULT_CURRENCY = "NGN";

    private final ListingRepository listingRepository;
    private final PropertyRepository propertyRepository;

    @Transactional
    public Listing create(Long callerId, CreateListingCommand cmd) {
        Property property = propertyRepository.findById(cmd.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(cmd.propertyId()));
        if (!property.getOwnerId().equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        Instant now = Instant.now();
        Listing saved = listingRepository.save(Listing.builder()
                .propertyId(property.getId())
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
     * Returns LIVE listings paired with their parent property. Two queries total
     * (listings page + bulk property fetch by id), not N+1 — small enough for the
     * default page size; switch to a JOIN-based query when listings exceed ~10k.
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
        Map<Long, Property> properties = propertyRepository.findAllById(propertyIds).stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));
        return listings.map(l -> new ListingWithProperty(l, properties.get(l.getPropertyId())));
    }

    /**
     * Public detail. Bumps {@code view_count} via an atomic UPDATE — lock-free, so it
     * doesn't churn the @Version on every page view, and returns immediately even when
     * Hibernate's first-level cache holds a stale entity. The response carries the
     * pre-increment value (close enough; the freshly-bumped count is visible on the
     * next read).
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
        Property property = propertyRepository.findById(listing.getPropertyId())
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
}
