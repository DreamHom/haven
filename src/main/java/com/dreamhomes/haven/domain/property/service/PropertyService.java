package com.dreamhomes.haven.domain.property.service;

import com.dreamhomes.haven.domain.property.dto.CreatePropertyRequest;
import com.dreamhomes.haven.domain.property.model.Property;
import com.dreamhomes.haven.domain.property.repository.PropertyRepository;
import com.dreamhomes.haven.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {
    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public Property create(CreatePropertyRequest req) {
        var p = new Property();
        p.setOwnerId(req.ownerId());
        p.setAddressLine1(req.addressLine1());
        p.setCity(req.city());
        return propertyRepository.save(p);
    }

    @Transactional(readOnly = true)
    public Property get(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property not found"));
    }
}

