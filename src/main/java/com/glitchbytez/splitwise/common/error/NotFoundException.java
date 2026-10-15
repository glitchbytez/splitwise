package com.glitchbytez.splitwise.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a requested resource does not exist or is not visible to the caller. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
