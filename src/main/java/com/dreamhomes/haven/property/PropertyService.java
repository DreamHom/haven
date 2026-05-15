package com.dreamhomes.haven.property;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import com.dreamhomes.haven.property.exception.InvalidPropertyForTypeException;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import com.dreamhomes.haven.property.model.Property;

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

    @Transactional
    public Property create(Long ownerId, CreatePropertyCommand cmd) {
        if (cmd.type().requiresRoomCounts() && (cmd.bedrooms() == null || cmd.bathrooms() == null)) {
            throw new InvalidPropertyForTypeException(
                    cmd.type() + " requires both bedrooms and bathrooms");
        }
        Property saved = propertyRepository.save(Property.builder()
                .ownerId(ownerId)
                .type(cmd.type())
                .address(cmd.address())
                .bedrooms(cmd.bedrooms())
                .bathrooms(cmd.bathrooms())
                .sizeSqm(cmd.sizeSqm())
                .description(cmd.description())
                .build());
        log.info("Created propertyId={} ownerId={} type={}", saved.getId(), ownerId, saved.getType());
        return saved;
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
