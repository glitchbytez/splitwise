package com.glitchbytez.splitwise.group;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a {@code null} field is left unchanged. An empty description clears it.
 */
public record UpdateGroupRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 500) String description) {

    @AssertTrue(message = "at least one of name or description must be provided")
    boolean isAnyFieldPresent() {
        return name != null || description != null;
    }
}
