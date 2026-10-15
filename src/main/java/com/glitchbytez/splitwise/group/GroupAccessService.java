package com.glitchbytez.splitwise.group;

import java.util.UUID;

import com.glitchbytez.splitwise.common.error.ForbiddenException;
import com.glitchbytez.splitwise.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that decides whether a user may act on a group. Every group-scoped service (groups,
 * expenses, settlements, balances, activity) calls this first.
 * <ul>
 *     <li>Group missing or deleted: 404</li>
 *     <li>Group exists but the caller is not an active member: 403</li>
 *     <li>Owner-only action by a plain member: 403</li>
 * </ul>
 */
@Service
public class GroupAccessService {

    static final String GROUP_NOT_FOUND = "Group not found";
    static final String NOT_A_MEMBER = "You are not a member of this group";
    static final String OWNER_ONLY = "Only the group owner can do this";

    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public GroupAccessService(GroupRepository groups, GroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public GroupMember requireMember(UUID groupId, UUID userId) {
        requireGroup(groupId);
        return members.findActive(groupId, userId).orElseThrow(() -> new ForbiddenException(NOT_A_MEMBER));
    }

    @Transactional(readOnly = true)
    public GroupMember requireOwner(UUID groupId, UUID userId) {
        GroupMember member = requireMember(groupId, userId);
        if (!member.isOwner()) {
            throw new ForbiddenException(OWNER_ONLY);
        }
        return member;
    }

    @Transactional(readOnly = true)
    public Group requireGroup(UUID groupId) {
        return groups.findByIdAndDeletedAtIsNull(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
    }
}
