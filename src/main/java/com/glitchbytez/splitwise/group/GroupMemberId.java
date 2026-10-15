package com.glitchbytez.splitwise.group;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class GroupMemberId implements Serializable {

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected GroupMemberId() {
        // for JPA
    }

    public GroupMemberId(UUID groupId, UUID userId) {
        this.groupId = Objects.requireNonNull(groupId);
        this.userId = Objects.requireNonNull(userId);
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof GroupMemberId other
                && groupId.equals(other.groupId) && userId.equals(other.userId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }

    @Override
    public String toString() {
        return "GroupMemberId[groupId=" + groupId + ", userId=" + userId + "]";
    }
}
