package com.dreamhomes.haven.property.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.property.model.Property;

public class PropertyNotFoundException extends DomainException {

    public PropertyNotFoundException(Long propertyId) {
        super(HttpStatus.NOT_FOUND, "Property " + propertyId + " was not found");
    }
}
