package com.glitchbytez.splitwise.auth;

import java.time.Duration;

/** @param expiresIn lifetime of the access token in seconds */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    static TokenResponse bearer(String accessToken, Duration ttl) {
        return new TokenResponse(accessToken, "Bearer", ttl.toSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
