package com.dreamhomes.haven.property;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a property's fields don't match the rules its {@link PropertyType} requires. */
public class InvalidPropertyForTypeException extends DomainException {

    public InvalidPropertyForTypeException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
