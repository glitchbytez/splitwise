package com.glitchbytez.splitwise.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when credentials are missing or wrong (e.g. a failed login). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
