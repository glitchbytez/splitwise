package com.glitchbytez.splitwise.group;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.glitchbytez.splitwise.activity.ActivityService;
import com.glitchbytez.splitwise.activity.ActivityType;
import com.glitchbytez.splitwise.common.error.ConflictException;
import com.glitchbytez.splitwise.common.error.ForbiddenException;
import com.glitchbytez.splitwise.common.error.NotFoundException;
import com.glitchbytez.splitwise.user.EmailAddresses;
import com.glitchbytez.splitwise.user.User;
import com.glitchbytez.splitwise.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership rules (spec 3.2):
 * <ul>
 *     <li>Any member can add a registered user by email; unknown emails are 404, existing members 409.</li>
 *     <li>Only the owner can remove other members; anyone but the owner can leave.</li>
 *     <li>Removing or leaving requires the member's balance in the group to be zero (409 otherwise).</li>
 * </ul>
 */
@Service
public class GroupMemberService {

    static final String USER_NOT_FOUND = "No registered user has this email";
    static final String ALREADY_MEMBER = "This user is already a member of the group";
    static final String MEMBER_NOT_FOUND = "Member not found";
    static final String OWNER_CANNOT_LEAVE = "The owner cannot leave the group; delete the group instead";
    static final String LEAVE_WITH_BALANCE = "You can only leave once your balance in this group is zero";
    static final String REMOVE_WITH_BALANCE = "A member can only be removed once their balance in this group is zero";
    static final String ENTITY_TYPE = "USER";

    private static final Logger log = LoggerFactory.getLogger(GroupMemberService.class);

    private final GroupMemberRepository members;
    private final UserRepository users;
    private final GroupAccessService access;
    private final MemberBalances balances;
    private final ActivityService activity;
    private final Clock clock;

    public GroupMemberService(GroupMemberRepository members, UserRepository users, GroupAccessService access,
                              MemberBalances balances, ActivityService activity, Clock clock) {
        this.members = members;
        this.users = users;
        this.access = access;
        this.balances = balances;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional
    public MemberResponse add(UUID groupId, UUID callerId, AddMemberRequest request) {
        access.requireMember(groupId, callerId);
        User user = users.findByEmailAndDeletedAtIsNull(EmailAddresses.normalize(request.email()))
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));

        GroupMember member = reactivateOrJoin(groupId, user.getId());
        activity.record(groupId, callerId, ActivityType.MEMBER_ADDED, ENTITY_TYPE, user.getId(), Map.of());
        log.info("User {} added user {} to group {}", callerId, user.getId(), groupId);
        return new MemberResponse(user.getId(), user.getDisplayName(), member.getRole(), member.getJoinedAt());
    }

    /** Removes {@code targetUserId}; when the caller removes themselves this is "leave group". */
    @Transactional
    public void remove(UUID groupId, UUID callerId, UUID targetUserId) {
        GroupMember caller = access.requireMember(groupId, callerId);
        boolean leaving = callerId.equals(targetUserId);
        GroupMember target = leaving ? requireCanLeave(caller) : requireRemovableBy(caller, targetUserId);

        if (!balances.isSettled(groupId, targetUserId)) {
            throw new ConflictException(leaving ? LEAVE_WITH_BALANCE : REMOVE_WITH_BALANCE);
        }
        target.leave(clock.instant());
        activity.record(groupId, callerId, leaving ? ActivityType.MEMBER_LEFT : ActivityType.MEMBER_REMOVED,
                ENTITY_TYPE, targetUserId, Map.of());
        log.info("User {} {} group {}", targetUserId, leaving ? "left" : "was removed from", groupId);
    }

    private GroupMember reactivateOrJoin(UUID groupId, UUID userId) {
        Optional<GroupMember> existing = members.findById(new GroupMemberId(groupId, userId));
        if (existing.isPresent()) {
            GroupMember member = existing.get();
            if (member.isActive()) {
                throw new ConflictException(ALREADY_MEMBER);
            }
            member.rejoin(clock.instant());
            return member;
        }
        try {
            return members.saveAndFlush(GroupMember.join(groupId, userId, GroupRole.MEMBER, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent add of the same user.
            throw new ConflictException(ALREADY_MEMBER);
        }
    }

    private static GroupMember requireCanLeave(GroupMember caller) {
        if (caller.isOwner()) {
            throw new ConflictException(OWNER_CANNOT_LEAVE);
        }
        return caller;
    }

    private GroupMember requireRemovableBy(GroupMember caller, UUID targetUserId) {
        if (!caller.isOwner()) {
            throw new ForbiddenException(GroupAccessService.OWNER_ONLY);
        }
        return members.findActive(caller.getGroupId(), targetUserId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND));
    }
}
