package com.dreamhomes.haven.property;

import com.dreamhomes.haven.common.config.CacheConfig;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.embedding.ListingSearchEmbeddingService;
import com.dreamhomes.haven.listing.model.ListingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.dreamhomes.haven.property.dto.CreatePropertyCommand;
import com.dreamhomes.haven.property.dto.PropertyResponse;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.property.dto.UpdatePropertyCommand;
import com.dreamhomes.haven.property.exception.InvalidPropertyForTypeException;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.model.Role;

/**
 * Implementation of {@link PropertyService}. The internal {@link #create} method is the
 * write entry point used by {@link PropertyController}; it returns the entity directly
 * because the controller is in the same module and can map to {@link PropertyResponse}
 * itself. Cross-feature consumers go through the API methods only.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;
    private final ListingRepository listingRepository;
    private final ListingSearchEmbeddingService listingSearchEmbeddingService;

    @Transactional
    public Property create(Long ownerId, CreatePropertyCommand cmd) {
        requireRoomCountsIfNeeded(cmd.type(), cmd.bedrooms(), cmd.bathrooms());
        Property saved = propertyRepository.save(Property.builder()
                .ownerId(ownerId)
                .type(cmd.type())
                .address(cmd.address())
                .bedrooms(cmd.bedrooms())
                .bathrooms(cmd.bathrooms())
                .sizeSqm(cmd.sizeSqm())
                .description(cmd.description())
                .latitude(cmd.latitude())
                .longitude(cmd.longitude())
                .build());
        log.info("Created propertyId={} ownerId={} type={}", saved.getId(), ownerId, saved.getType());
        return saved;
    }

    /**
     * Partial update. Caller must own the property or be {@link Role#ADMIN}. {@code type}
     * is immutable on this path — bedroom/bathroom rules are re-checked after each patch.
     *
     * <p>Every listing payload embeds a {@link PropertySummary} snapshot, so a property
     * patch must also wipe both listing caches — the detail cache (because every snapshot
     * we cached for any listing of this property is now stale) and the browse cache
     * (same reason). We flush both namespaces wholesale rather than locating the affected
     * listing ids ahead of time because the cost of recomputing the next browse page is
     * tiny compared to the alternative of returning out-of-date geo / bedroom counts.</p>
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.LISTINGS_DETAIL, allEntries = true),
            @CacheEvict(value = CacheConfig.LISTINGS_BROWSE, allEntries = true)
    })
    public PropertyResponse update(Long callerUserId, Role role, Long propertyId, UpdatePropertyCommand cmd) {
        Property p = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
        boolean owner = p.getOwnerId().equals(callerUserId);
        boolean admin = role == Role.ADMIN;
        if (!owner && !admin) {
            throw new PropertyNotFoundException(propertyId);
        }
        if (cmd.address() != null) {
            p.setAddress(cmd.address());
        }
        if (cmd.bedrooms() != null) {
            p.setBedrooms(cmd.bedrooms());
        }
        if (cmd.bathrooms() != null) {
            p.setBathrooms(cmd.bathrooms());
        }
        if (cmd.sizeSqm() != null) {
            p.setSizeSqm(cmd.sizeSqm());
        }
        if (cmd.description() != null) {
            p.setDescription(cmd.description());
        }
        if (cmd.latitude() != null && cmd.longitude() != null) {
            p.setLatitude(cmd.latitude());
            p.setLongitude(cmd.longitude());
        }
        requireRoomCountsIfNeeded(p.getType(), p.getBedrooms(), p.getBathrooms());
        Property saved = propertyRepository.save(p);
        log.info("Updated propertyId={} by userId={} admin={}", propertyId, callerUserId, admin);
        for (var li : listingRepository.findByPropertyId(propertyId)) {
            if (li.getStatus() == ListingStatus.LIVE) {
                listingSearchEmbeddingService.scheduleRefreshListing(li.getId());
            }
        }
        return propertyMapper.toResponse(saved);
    }

    private static void requireRoomCountsIfNeeded(PropertyType type, Integer bedrooms, Integer bathrooms) {
        if (type.requiresRoomCounts() && (bedrooms == null || bathrooms == null)) {
            throw new InvalidPropertyForTypeException(
                    type + " requires both bedrooms and bathrooms");
        }
    }

    @Transactional(readOnly = true)
    public PropertyResponse findById(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .map(propertyMapper::toResponse)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
    }

    /**
     * Owner's portfolio. Backs {@code GET /api/properties/mine} — the read-side
     * the persona audit (Amaka, Biodun) flagged as missing for owners with even
     * a handful of properties.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PropertyResponse> listMine(
            Long ownerId, org.springframework.data.domain.Pageable pageable) {
        return propertyRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable)
                .map(propertyMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<PropertySummary> findSummary(Long propertyId) {
        return propertyRepository.findById(propertyId).map(propertyMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<Long, PropertySummary> findSummariesByIds(Collection<Long> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PropertySummary> out = new LinkedHashMap<>();
        for (Property p : propertyRepository.findAllById(propertyIds)) {
            out.put(p.getId(), propertyMapper.toSummary(p));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<Long> ownerOf(Long propertyId) {
        return propertyRepository.findById(propertyId).map(Property::getOwnerId);
    }

    @Transactional
    public void markDocumentsVerified(Long propertyId, Instant when) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
        property.setDocumentsVerifiedAt(when);
        propertyRepository.save(property);
    }
}
