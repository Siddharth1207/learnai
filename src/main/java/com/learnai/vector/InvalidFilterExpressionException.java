package com.learnai.vector;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFilterExpressionException extends RuntimeException {

    public InvalidFilterExpressionException(String filter, Throwable cause) {
        super("Invalid filter expression '" + filter + "': " + cause.getMessage(), cause);
    }
}
