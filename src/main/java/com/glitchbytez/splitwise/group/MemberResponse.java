package com.glitchbytez.splitwise.group;

import java.time.Instant;
import java.util.UUID;

/** A group member as other members see them: no email (data minimisation). */
public record MemberResponse(UUID userId, String displayName, GroupRole role, Instant joinedAt) {

    static MemberResponse from(GroupMemberRepository.MemberView view) {
        return new MemberResponse(view.getUserId(), view.getDisplayName(), view.getRole(), view.getJoinedAt());
    }
}
