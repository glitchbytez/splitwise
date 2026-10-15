package com.glitchbytez.splitwise.activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One immutable line of a group's activity feed. The payload holds ids and amounts, never personal data. */
@Entity
@Table(name = "activity_log")
public class ActivityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private ActivityType type;

    @Column(name = "entity_type", length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityEntry() {
        // for JPA
    }

    ActivityEntry(UUID groupId, UUID actorId, ActivityType type, String entityType, UUID entityId,
                  Map<String, Object> payload) {
        this.groupId = groupId;
        this.actorId = actorId;
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public ActivityType getType() {
        return type;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
