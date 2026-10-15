package com.glitchbytez.splitwise.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings bound from {@code app.jwt.*}. The secret comes from the {@code JWT_SECRET} environment variable
 * and must be at least 256 bits, the minimum key size for HS256.
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret != null && !secret.isBlank() && secretBytes(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
    }

    byte[] secretBytes() {
        return secretBytes(secret);
    }

    private static byte[] secretBytes(String secret) {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
