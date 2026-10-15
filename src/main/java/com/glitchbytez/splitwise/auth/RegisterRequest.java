package com.glitchbytez.splitwise.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull @Size(min = AuthService.MIN_PASSWORD_LENGTH, max = AuthService.MAX_PASSWORD_BYTES) String password) {

    @Override
    public String toString() {
        // Never let the password reach a log line.
        return "RegisterRequest[displayName=" + displayName + ", email=***, password=***]";
    }
}
