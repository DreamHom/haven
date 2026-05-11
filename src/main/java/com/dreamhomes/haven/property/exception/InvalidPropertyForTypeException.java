package com.dreamhomes.haven.property.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;
import com.dreamhomes.haven.property.model.PropertyType;

/** Thrown when a property's fields don't match the rules its {@link PropertyType} requires. */
public class InvalidPropertyForTypeException extends DomainException {

    public InvalidPropertyForTypeException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
