package com.glitchbytez.splitwise.group;

import java.time.Instant;
import java.util.UUID;

public record GroupSummaryResponse(
        UUID id,
        String name,
        String description,
        GroupRole myRole,
        long memberCount,
        Instant createdAt) {

    static GroupSummaryResponse from(GroupRepository.GroupSummaryView view) {
        return new GroupSummaryResponse(view.getId(), view.getName(), view.getDescription(), view.getMyRole(),
                view.getMemberCount(), view.getCreatedAt());
    }
}
