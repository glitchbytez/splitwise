package com.glitchbytez.splitwise.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=***, password=***]";
    }
}
