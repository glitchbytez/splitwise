package com.glitchbytez.splitwise.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a request is well-formed but violates a business rule (e.g. shares do not sum to the total). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
