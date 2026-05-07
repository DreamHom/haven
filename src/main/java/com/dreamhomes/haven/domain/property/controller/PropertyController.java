package com.dreamhomes.haven.domain.property.controller;

import com.dreamhomes.haven.domain.property.dto.CreatePropertyRequest;
import com.dreamhomes.haven.domain.property.dto.PropertyResponse;
import com.dreamhomes.haven.domain.property.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/properties")
public class PropertyController {
    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@Valid @RequestBody CreatePropertyRequest req) {
        var p = propertyService.create(req);
        return new PropertyResponse(p.getId(), p.getOwnerId(), p.getAddressLine1(), p.getCity(), p.getStatus());
    }

    @GetMapping("/{id}")
    public PropertyResponse get(@PathVariable Long id) {
        var p = propertyService.get(id);
        return new PropertyResponse(p.getId(), p.getOwnerId(), p.getAddressLine1(), p.getCity(), p.getStatus());
    }
}

