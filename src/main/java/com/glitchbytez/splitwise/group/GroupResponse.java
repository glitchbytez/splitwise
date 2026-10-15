package com.glitchbytez.splitwise.group;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        UUID createdBy,
        Instant createdAt,
        GroupRole myRole,
        List<MemberResponse> members) {
}
