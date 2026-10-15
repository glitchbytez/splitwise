package com.glitchbytez.splitwise.group;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A user's membership of a group. Leaving sets {@code leftAt} instead of deleting the row, so historic expenses
 * keep a valid member reference; re-adding the same user reactivates the row (the primary key is (group, user)).
 */
@Entity
@Table(name = "group_members")
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    protected GroupMember() {
        // for JPA
    }

    private GroupMember(GroupMemberId id, GroupRole role, Instant joinedAt) {
        this.id = id;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    static GroupMember join(UUID groupId, UUID userId, GroupRole role, Instant now) {
        return new GroupMember(new GroupMemberId(groupId, userId), role, now);
    }

    void leave(Instant now) {
        this.leftAt = now;
    }

    /** Brings a former member back as an ordinary member. */
    void rejoin(Instant now) {
        this.role = GroupRole.MEMBER;
        this.joinedAt = now;
        this.leftAt = null;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public boolean isOwner() {
        return role == GroupRole.OWNER;
    }

    public GroupMemberId getId() {
        return id;
    }

    public UUID getGroupId() {
        return id.getGroupId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public GroupRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof GroupMember other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "GroupMember[" + id + ", role=" + role + "]";
    }
}
