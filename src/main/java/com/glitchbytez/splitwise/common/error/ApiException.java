package com.glitchbytez.splitwise.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for business errors that map directly to an HTTP status.
 * The message becomes the {@code detail} of the RFC 7807 response, so it must be safe to show to clients.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
