package com.learnai.property;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PropertyNotAvailableException extends RuntimeException {

    public PropertyNotAvailableException(String propertyId) {
        super("Property " + propertyId + " has already been sold");
    }
}
