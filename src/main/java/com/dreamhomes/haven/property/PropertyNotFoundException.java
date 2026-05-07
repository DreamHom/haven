package com.dreamhomes.haven.property;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

public class PropertyNotFoundException extends DomainException {

    public PropertyNotFoundException(Long propertyId) {
        super(HttpStatus.NOT_FOUND, "Property " + propertyId + " was not found");
    }
}
