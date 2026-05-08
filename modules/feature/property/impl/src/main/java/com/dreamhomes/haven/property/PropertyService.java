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

/**
 * Implementation of {@link PropertyApi}. The internal {@link #create} method is the
 * write entry point used by {@link PropertyController}; it returns the entity directly
 * because the controller is in the same module and can map to {@link PropertyResponse}
 * itself. Cross-feature consumers go through the API methods only.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PropertyService implements PropertyApi {

    private final PropertyRepository propertyRepository;

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
                .createdAt(Instant.now())
                .build());
        log.info("Created propertyId={} ownerId={} type={}", saved.getId(), ownerId, saved.getType());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse findById(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .map(PropertyService::toResponse)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PropertySummary> findSummary(Long propertyId) {
        return propertyRepository.findById(propertyId).map(PropertyService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, PropertySummary> findSummariesByIds(Collection<Long> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PropertySummary> out = new LinkedHashMap<>();
        for (Property p : propertyRepository.findAllById(propertyIds)) {
            out.put(p.getId(), toSummary(p));
        }
        return out;
    }

    /** Internal entity → API DTO mappers. Kept private to this module. */
    static PropertySummary toSummary(Property p) {
        return new PropertySummary(p.getId(), p.getType(), p.getAddress(),
                p.getBedrooms(), p.getBathrooms(), p.getSizeSqm(), p.getDocumentsVerifiedAt());
    }

    static PropertyResponse toResponse(Property p) {
        return new PropertyResponse(p.getId(), p.getOwnerId(), p.getType(), p.getAddress(),
                p.getBedrooms(), p.getBathrooms(), p.getSizeSqm(), p.getDescription(),
                p.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> ownerOf(Long propertyId) {
        return propertyRepository.findById(propertyId).map(Property::getOwnerId);
    }

    @Override
    @Transactional
    public void markDocumentsVerified(Long propertyId, Instant when) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
        property.setDocumentsVerifiedAt(when);
        propertyRepository.save(property);
    }
}
