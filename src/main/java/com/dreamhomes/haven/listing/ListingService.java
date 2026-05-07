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

    @Transactional(readOnly = true)
    public Page<Listing> browsePublic(Pageable pageable) {
        return listingRepository.findByStatus(ListingStatus.LIVE, pageable);
    }

    @Transactional(readOnly = true)
    public Listing findPubliclyVisible(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        // Only LIVE listings are reachable to anonymous callers — paused/closed look 404
        // to the public to avoid leaking that they ever existed.
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new ListingNotFoundException(listingId);
        }
        return listing;
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
