package com.learnai.research;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when the internet research provider has no API key, so the paid report cannot be produced. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ResearchNotConfiguredException extends RuntimeException {

    public ResearchNotConfiguredException(String message) {
        super(message);
    }
}
