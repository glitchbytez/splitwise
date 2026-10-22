package com.glitchbytez.splitwise.activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        ActivityType type,
        UUID actorId,
        String entityType,
        UUID entityId,
        Map<String, Object> payload,
        Instant createdAt) {

    static ActivityResponse from(ActivityEntry entry) {
        return new ActivityResponse(entry.getId(), entry.getType(), entry.getActorId(), entry.getEntityType(),
                entry.getEntityId(), entry.getPayload() == null ? Map.of() : entry.getPayload(), entry.getCreatedAt());
    }
}
