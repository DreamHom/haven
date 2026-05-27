package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
import com.dreamhomes.haven.common.config.CacheConfig;
import com.dreamhomes.haven.listing.embedding.ListingSearchEmbeddingService;
import com.dreamhomes.haven.listing.exception.AgentCannotEditListingFieldsException;
import com.dreamhomes.haven.listing.exception.ListingDuplicateOpenForTypeException;
import com.dreamhomes.haven.listingreport.ListingReportRepository;
import com.dreamhomes.haven.listingreport.model.ListingReportStatus;
import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import com.dreamhomes.haven.property.dto.PropertySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private final ListingSearchEmbeddingService listingSearchEmbeddingService;

    @Transactional
    // A brand-new LIVE listing must show up in the next browse fetch — wipe the browse
    // cache the moment we save. Detail cache is keyed by listing id (which doesn't exist
    // yet), so no detail entry can be stale; only the browse namespace needs flushing.
    @CacheEvict(value = CacheConfig.LISTINGS_BROWSE, allEntries = true)
    public Listing create(Long callerId, CreateListingCommand cmd) {
        Long propertyOwner = propertyService.ownerOf(cmd.propertyId())
                .orElseThrow(() -> new PropertyNotFoundException(cmd.propertyId()));
        if (!propertyOwner.equals(callerId)) {
            throw new NotPropertyOwnerException();
        }

        // Item 12 — at most one LIVE listing per (property, listing_type). Pre-check
        // with a friendly exception; V47's partial UQ is the race safety net below.
        if (listingRepository.existsByPropertyIdAndListingTypeAndStatus(
                cmd.propertyId(), cmd.listingType(), ListingStatus.LIVE)) {
            throw new ListingDuplicateOpenForTypeException(cmd.propertyId(), cmd.listingType());
        }

        Listing saved;
        try {
            saved = listingRepository.save(Listing.builder()
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
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Two concurrent creates passed the pre-check; the partial UQ refused this one.
            // Translate to the same 409 so callers see one story.
            throw new ListingDuplicateOpenForTypeException(cmd.propertyId(), cmd.listingType());
        }
        log.info("Created listingId={} propertyId={} ownerId={} type={}",
                saved.getId(), saved.getPropertyId(), callerId, saved.getListingType());
        listingSearchEmbeddingService.scheduleRefreshListing(saved.getId());
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
     *
     * <p>Server-cached: a 60-second TTL matches the {@code Cache-Control: max-age=60}
     * we stamp on the response, so the public layer (CDN/browser) and the in-process
     * layer agree. The cache key bakes in every filter param plus the {@code Pageable}
     * (page / size / sort), so different filter sets are distinct entries. Writes that
     * affect public visibility evict the whole namespace ({@code allEntries = true})
     * because we can't know which filter combinations the listing belongs to.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.LISTINGS_BROWSE,
            key = "T(java.util.Objects).hash(#listingType, #priceMin, #priceMax, #bedrooms, "
                    + "#propertyType, #location, #pageable)")
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

    /**
     * LIVE listings with property summaries, preserving the caller's id order (for Dream AI vector hits).
     */
    @Transactional(readOnly = true)
    public List<ListingWithProperty> findLiveWithSummariesInOrder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return List.of();
        }
        List<Listing> found = listingRepository.findAllById(new HashSet<>(orderedIds));
        Map<Long, Listing> byId = found.stream().collect(Collectors.toMap(Listing::getId, l -> l));
        List<Listing> orderedLive = new ArrayList<>();
        for (Long id : orderedIds) {
            Listing l = byId.get(id);
            if (l != null && l.getStatus() == ListingStatus.LIVE) {
                orderedLive.add(l);
            }
        }
        return withSummariesList(orderedLive);
    }

    private Page<ListingWithProperty> withSummaries(Page<Listing> listings) {
        if (listings.isEmpty()) {
            return listings.map(l -> new ListingWithProperty(l, null, null));
        }
        return new PageImpl<>(withSummariesList(listings.getContent()), listings.getPageable(), listings.getTotalElements());
    }

    private List<ListingWithProperty> withSummariesList(List<Listing> listings) {
        if (listings.isEmpty()) {
            return List.of();
        }
        Set<Long> propertyIds = listings.stream()
                .map(Listing::getPropertyId)
                .collect(Collectors.toSet());
        Map<Long, PropertySummary> summaries = propertyService.findSummariesByIds(propertyIds);
        Set<Long> ownerIds = listings.stream().map(Listing::getOwnerId).collect(Collectors.toSet());
        Map<Long, OwnerTrust> trust = loadOwnerTrust(ownerIds);
        List<ListingWithProperty> out = new ArrayList<>(listings.size());
        for (Listing l : listings) {
            OwnerTrust t = trust.get(l.getOwnerId());
            out.add(new ListingWithProperty(l, summaries.get(l.getPropertyId()),
                    t == null ? null : t.bio(),
                    t == null ? null : t.identityVerifiedAt()));
        }
        return out;
    }

    /**
     * One-shot lookup of bio + identity-verified-at per owner so the listing payload
     * can render trust signals (Item 16) without an N+1 fetch from the frontend.
     */
    private Map<Long, OwnerTrust> loadOwnerTrust(Set<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OwnerTrust> out = new HashMap<>();
        for (com.dreamhomes.haven.user.repository.OwnerTrustRow row
                : userRepository.findOwnerTrustByUserIds(ownerIds)) {
            out.put(row.getOwnerId(), new OwnerTrust(row.getPublicBio(), row.getIdentityVerifiedAt()));
        }
        return out;
    }

    private record OwnerTrust(String bio, Instant identityVerifiedAt) {}

    /**
     * Public detail — pure read, server-cached for the same 60s TTL as the
     * {@code Cache-Control: max-age=60} header on the response. The view-count
     * increment was deliberately split off into {@link #recordPublicView(Long)} so
     * the counter reflects every hit (not just cache misses).
     *
     * <p>Throws from inside the cache miss are NOT cached (Spring's default), so a
     * later publish of a previously-missing id is read immediately.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.LISTINGS_DETAIL, key = "#listingId")
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
        // Single-owner trust lookup — bio + identity-verified-at in one round trip so
        // the response carries the "Possible Scam" signal without an N+1 from Vista.
        com.dreamhomes.haven.user.repository.OwnerTrustRow trustRow =
                userRepository.findOwnerTrustByUserId(listing.getOwnerId()).orElse(null);
        String ownerBio = trustRow == null ? null : trustRow.getPublicBio();
        Instant ownerIdentityVerifiedAt = trustRow == null ? null : trustRow.getIdentityVerifiedAt();
        return new ListingWithProperty(listing, property, ownerBio, ownerIdentityVerifiedAt);
    }

    /**
     * Atomically increments {@code view_count} for a single public detail hit. Kept
     * outside the {@link #findPubliclyVisible(Long)} cache so engagement metrics
     * reflect real anonymous traffic rather than cache-miss-only traffic.
     */
    @Transactional
    public void recordPublicView(Long listingId) {
        listingRepository.incrementViewCount(listingId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.LISTINGS_DETAIL, key = "#listingId"),
            @CacheEvict(value = CacheConfig.LISTINGS_BROWSE, allEntries = true)
    })
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
        // Item 12 — surfacing a PAUSED listing back to LIVE must respect the
        // one-LIVE-per-(property, listing_type) rule too. Pre-check before save so the
        // user sees the same friendly 409 they'd get on create.
        if (cmd.status() == ListingStatus.LIVE
                && listing.getStatus() != ListingStatus.LIVE
                && listingRepository.existsByPropertyIdAndListingTypeAndStatus(
                        listing.getPropertyId(), listing.getListingType(), ListingStatus.LIVE)) {
            throw new ListingDuplicateOpenForTypeException(
                    listing.getPropertyId(), listing.getListingType());
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
        Listing saved;
        try {
            saved = listingRepository.save(listing);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race-safety net for the partial UQ from V47 when a concurrent transition
            // raced past the pre-check above. Maps to the same 409 callers already know.
            throw new ListingDuplicateOpenForTypeException(
                    listing.getPropertyId(), listing.getListingType());
        }
        log.info("Updated listingId={} callerId={} role={} status={} price={}",
                saved.getId(), callerId, role, saved.getStatus(), saved.getAskingPrice());
        listingSearchEmbeddingService.scheduleRefreshListing(saved.getId());
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
        com.dreamhomes.haven.user.repository.OwnerTrustRow trustRow =
                userRepository.findOwnerTrustByUserId(listing.getOwnerId()).orElse(null);
        String ownerBio = trustRow == null ? null : trustRow.getPublicBio();
        Instant ownerIdentityVerifiedAt = trustRow == null ? null : trustRow.getIdentityVerifiedAt();
        return listingMapper.toResponse(listing, summary,
                activeAgentUserId(listingId), pendingReportCount(listingId),
                ownerBio, ownerIdentityVerifiedAt);
    }

    /** Owner's {@code users.public_bio} for embedding on listing payloads (create/update/browse). */
    @Transactional(readOnly = true)
    public Optional<String> findOwnerPublicBio(Long ownerUserId) {
        return userRepository.findPublicBioByUserId(ownerUserId);
    }

    /**
     * Combined owner bio + identity-verified-at lookup for create/update/single-owner
     * detail paths. Returns a snapshot whose fields may both be null (deleted owner /
     * empty bio / unverified identity), letting the caller embed both trust signals in
     * one round trip — see Item 16 in {@code docs/demo-prep/post-session-tasks.md}.
     */
    @Transactional(readOnly = true)
    public OwnerTrustSnapshot findOwnerTrust(Long ownerUserId) {
        return userRepository.findOwnerTrustByUserId(ownerUserId)
                .map(row -> new OwnerTrustSnapshot(row.getPublicBio(), row.getIdentityVerifiedAt()))
                .orElse(OwnerTrustSnapshot.EMPTY);
    }

    /**
     * Read-only snapshot of the owner-side trust signals embedded on listing payloads.
     * {@code identityVerifiedAt} is null when the owner has not completed identity
     * verification — Vista renders the "⚠️ Possible Scam" warning chip in that case.
     */
    public record OwnerTrustSnapshot(String publicBio, Instant identityVerifiedAt) {
        public static final OwnerTrustSnapshot EMPTY = new OwnerTrustSnapshot(null, null);
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

    /**
     * Subset of {@code ids} that still reference a {@link ListingStatus#LIVE} row — used to
     * rehydrate Dream AI history without surfacing stale catalogue ids to the client.
     */
    @Transactional(readOnly = true)
    public List<Long> liveListingIdsAmong(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return listingRepository.findLiveIdsAmongIds(new java.util.ArrayList<>(ids));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.LISTINGS_DETAIL, key = "#listingId"),
            @CacheEvict(value = CacheConfig.LISTINGS_BROWSE, allEntries = true)
    })
    public void markApproved(Long listingId, Instant when) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        listing.setApprovedAt(when);
        listing.setUpdatedAt(when);
        listingRepository.save(listing);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.LISTINGS_DETAIL, key = "#listingId"),
            @CacheEvict(value = CacheConfig.LISTINGS_BROWSE, allEntries = true)
    })
    public void forceStatus(Long listingId, ListingStatus status, Instant when) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        listing.setStatus(status);
        listing.setUpdatedAt(when);
        listingRepository.save(listing);
    }

}
