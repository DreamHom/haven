package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
import com.dreamhomes.haven.listing.exception.AgentCannotEditListingFieldsException;
import com.dreamhomes.haven.listingreport.ListingReportRepository;
import com.dreamhomes.haven.listingreport.model.ListingReportStatus;
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
import java.util.HashMap;
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
import com.dreamhomes.haven.user.model.Role;

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
    private final AgentListingRepository agentListingRepository;
    private final ListingReportRepository listingReportRepository;
    private final com.dreamhomes.haven.user.repository.UserRepository userRepository;

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
                .title(cmd.title())
                .description(cmd.description())
                .headline(cmd.headline())
                .handoverDate(cmd.handoverDate())
                .virtualTourUrl(cmd.virtualTourUrl())
                .floorPlanUrl(cmd.floorPlanUrl())
                .priceNegotiable(cmd.priceNegotiable())
                .petsAllowed(cmd.petsAllowed())
                .utilitiesNote(cmd.utilitiesNote())
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
     * Filtered browse. Every parameter is optional. Persona audit (Temi, Ngozi, Emeka)
     * flagged that the catalogue was a single unsorted, unfiltered dump and that
     * query params like {@code ?location=Yaba&bedrooms=2} were silently ignored.
     */
    @Transactional(readOnly = true)
    public Page<ListingWithProperty> browsePublic(
            com.dreamhomes.haven.listing.model.ListingType listingType,
            java.math.BigDecimal priceMin, java.math.BigDecimal priceMax,
            Integer bedrooms,
            com.dreamhomes.haven.property.model.PropertyType propertyType,
            String location,
            Pageable pageable) {
        // If no filter is set, hit the simpler index-only query.
        if (listingType == null && priceMin == null && priceMax == null
                && bedrooms == null && propertyType == null
                && (location == null || location.isBlank())) {
            return browsePublic(pageable);
        }
        String locationFragment = (location == null || location.isBlank()) ? null : location;
        return withSummaries(listingRepository.searchLive(
                listingType, priceMin, priceMax, bedrooms, propertyType, locationFragment, pageable));
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
            return listings.map(l -> new ListingWithProperty(l, null, null));
        }
        Set<Long> propertyIds = listings.stream()
                .map(Listing::getPropertyId)
                .collect(Collectors.toSet());
        Map<Long, PropertySummary> summaries = propertyService.findSummariesByIds(propertyIds);
        Set<Long> ownerIds = listings.stream().map(Listing::getOwnerId).collect(Collectors.toSet());
        Map<Long, String> bios = loadOwnerBios(ownerIds);
        return listings.map(l -> new ListingWithProperty(l, summaries.get(l.getPropertyId()),
                bios.get(l.getOwnerId())));
    }

    private Map<Long, String> loadOwnerBios(Set<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        for (com.dreamhomes.haven.user.repository.OwnerPublicBioRow row
                : userRepository.findPublicBiosByUserIds(ownerIds)) {
            out.put(row.getOwnerId(), row.getPublicBio());
        }
        return out;
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
        String ownerBio = userRepository.findPublicBioByUserId(listing.getOwnerId()).orElse(null);
        listingRepository.incrementViewCount(listingId);
        return new ListingWithProperty(listing, property, ownerBio);
    }

    @Transactional
    public Listing update(Long callerId, Role role, Long listingId, UpdateListingCommand cmd) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        boolean owner = listing.getOwnerId().equals(callerId);
        boolean assignedAgent = role == Role.AGENT
                && agentListingRepository.existsByListingIdAndAgentUserIdAndStatus(
                listingId, callerId, AgentListingStatus.ACCEPTED);
        if (!owner && !assignedAgent) {
            throw new NotPropertyOwnerException();
        }
        if (assignedAgent && !owner && (cmd.status() != null || cmd.askingPrice() != null)) {
            throw new AgentCannotEditListingFieldsException();
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
        if (cmd.title() != null) {
            listing.setTitle(cmd.title());
        }
        if (cmd.description() != null) {
            listing.setDescription(cmd.description());
        }
        if (cmd.headline() != null) {
            listing.setHeadline(cmd.headline());
        }
        if (cmd.handoverDate() != null) {
            listing.setHandoverDate(cmd.handoverDate());
        }
        if (cmd.virtualTourUrl() != null) {
            listing.setVirtualTourUrl(trimTourUrl(cmd.virtualTourUrl()));
        }
        if (cmd.floorPlanUrl() != null) {
            listing.setFloorPlanUrl(trimTourUrl(cmd.floorPlanUrl()));
        }
        if (cmd.priceNegotiable() != null) {
            listing.setPriceNegotiable(cmd.priceNegotiable());
        }
        if (cmd.petsAllowed() != null) {
            listing.setPetsAllowed(trimToNull(cmd.petsAllowed()));
        }
        if (cmd.utilitiesNote() != null) {
            listing.setUtilitiesNote(trimToNull(cmd.utilitiesNote()));
        }
        Listing saved = listingRepository.save(listing);
        log.info("Updated listingId={} callerId={} role={} status={} price={}",
                saved.getId(), callerId, role, saved.getStatus(), saved.getAskingPrice());
        return saved;
    }

    /**
     * Owner-driven state machine.
     * <ul>
     *   <li>CLOSED is terminal — owner cannot move out of it.</li>
     *   <li>TAKEN_DOWN is admin-only — the owner cannot un-take-down their own listing
     *       via PATCH. Admins do that via {@code POST /admin/listings/{id}/approve}.</li>
     *   <li>Everything else (LIVE ↔ PAUSED, LIVE → CLOSED, PAUSED → CLOSED) is free.</li>
     * </ul>
     */
    private static boolean isAllowedTransition(ListingStatus from, ListingStatus to) {
        return from != ListingStatus.CLOSED && from != ListingStatus.TAKEN_DOWN;
    }

    private static String trimTourUrl(String raw) {
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    @Transactional(readOnly = true)
    public Page<ListingWithProperty> adminCatalog(ListingStatus status, Pageable pageable) {
        return withSummaries(listingRepository.adminCatalog(status, pageable));
    }

    // ============================ ListingService ============================

    @Transactional(readOnly = true)
    public ListingResponse findById(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        PropertySummary summary = propertyService.findSummary(listing.getPropertyId()).orElse(null);
        String ownerBio = userRepository.findPublicBioByUserId(listing.getOwnerId()).orElse(null);
        return listingMapper.toResponse(listing, summary,
                activeAgentUserId(listingId), pendingReportCount(listingId), ownerBio);
    }

    /** Owner's {@code users.public_bio} for embedding on listing payloads (create/update/browse). */
    @Transactional(readOnly = true)
    public Optional<String> findOwnerPublicBio(Long ownerUserId) {
        return userRepository.findPublicBioByUserId(ownerUserId);
    }

    /** Active agent assigned to this listing (ACCEPTED row), or null when none. */
    @Transactional(readOnly = true)
    public Long activeAgentUserId(Long listingId) {
        return agentListingRepository
                .findFirstByListingIdAndStatus(listingId, AgentListingStatus.ACCEPTED)
                .map(a -> a.getAgentUserId())
                .orElse(null);
    }

    /** Number of PENDING reports filed against this listing — drives the trust pill. */
    @Transactional(readOnly = true)
    public long pendingReportCount(Long listingId) {
        return listingReportRepository.countByListingIdAndStatus(
                listingId, ListingReportStatus.PENDING);
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
