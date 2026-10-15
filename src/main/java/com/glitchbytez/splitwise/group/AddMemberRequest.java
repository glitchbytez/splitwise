package com.glitchbytez.splitwise.group;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddMemberRequest(@NotBlank @Email @Size(max = 320) String email) {

    @Override
    public String toString() {
        return "AddMemberRequest[email=***]";
    }
}
