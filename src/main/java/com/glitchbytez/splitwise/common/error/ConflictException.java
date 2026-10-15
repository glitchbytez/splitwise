package com.glitchbytez.splitwise.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a request conflicts with current state (e.g. email taken, non-zero balance on leave). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
