package com.dreamhomes.haven.domain.property.service;

import com.dreamhomes.haven.domain.property.dto.CreatePropertyRequest;
import com.dreamhomes.haven.domain.property.model.Property;
import com.dreamhomes.haven.domain.property.repository.PropertyRepository;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PropertyService {
    private final PropertyRepository propertyRepository;

    @Transactional
    public Property create(CreatePropertyRequest req) {

        var p = new Property();
        p.setOwnerId(req.ownerId());
        p.setAddressLine1(req.addressLine1());
        p.setCity(req.city());
        p.setState(req.state());
        p.setCountry(req.country());
        return propertyRepository.save(p);
    }

    @Transactional(readOnly = true)
    public Property get(Long id) {
        return propertyRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Property not found! Create a new property first."));
    }
}

