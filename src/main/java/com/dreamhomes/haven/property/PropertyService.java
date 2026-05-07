package com.dreamhomes.haven.property;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class PropertyService {

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
}
