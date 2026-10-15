package com.glitchbytez.splitwise.group;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.glitchbytez.splitwise.activity.ActivityService;
import com.glitchbytez.splitwise.activity.ActivityType;
import com.glitchbytez.splitwise.common.web.PageResponse;
import com.glitchbytez.splitwise.common.web.Pageables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "createdAt");
    static final String ENTITY_TYPE = "GROUP";

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupAccessService access;
    private final ActivityService activity;
    private final Clock clock;

    public GroupService(GroupRepository groups, GroupMemberRepository members, GroupAccessService access,
                        ActivityService activity, Clock clock) {
        this.groups = groups;
        this.members = members;
        this.access = access;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional
    public GroupResponse create(UUID callerId, CreateGroupRequest request) {
        Group group = groups.saveAndFlush(
                Group.create(request.name().trim(), blankToNull(request.description()), callerId));
        members.save(GroupMember.join(group.getId(), callerId, GroupRole.OWNER, clock.instant()));
        activity.record(group.getId(), callerId, ActivityType.GROUP_CREATED, ENTITY_TYPE, group.getId(), Map.of());
        log.info("User {} created group {}", callerId, group.getId());
        return toResponse(group, GroupRole.OWNER);
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupSummaryResponse> listForMember(UUID callerId, Pageable pageable) {
        Pageables.requireSortableBy(pageable, SORTABLE_PROPERTIES);
        return PageResponse.from(groups.findSummariesForMember(callerId, pageable), GroupSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public GroupResponse get(UUID groupId, UUID callerId) {
        GroupMember caller = access.requireMember(groupId, callerId);
        return toResponse(access.requireGroup(groupId), caller.getRole());
    }

    @Transactional
    public GroupResponse update(UUID groupId, UUID callerId, UpdateGroupRequest request) {
        GroupMember owner = access.requireOwner(groupId, callerId);
        Group group = access.requireGroup(groupId);
        List<String> changed = new ArrayList<>();
        if (request.name() != null) {
            group.rename(request.name().trim());
            changed.add("name");
        }
        if (request.description() != null) {
            group.describe(blankToNull(request.description()));
            changed.add("description");
        }
        activity.record(groupId, callerId, ActivityType.GROUP_UPDATED, ENTITY_TYPE, groupId,
                Map.of("changed", changed));
        return toResponse(group, owner.getRole());
    }

    /** Soft delete: the group disappears for everyone, but its history stays in the database. */
    @Transactional
    public void delete(UUID groupId, UUID callerId) {
        access.requireOwner(groupId, callerId);
        access.requireGroup(groupId).markDeleted(clock.instant());
        activity.record(groupId, callerId, ActivityType.GROUP_DELETED, ENTITY_TYPE, groupId, Map.of());
        log.info("User {} deleted group {}", callerId, groupId);
    }

    private GroupResponse toResponse(Group group, GroupRole callerRole) {
        List<MemberResponse> memberList = members.findActiveMembers(group.getId()).stream()
                .map(MemberResponse::from)
                .toList();
        return new GroupResponse(group.getId(), group.getName(), group.getDescription(), group.getCreatedBy(),
                group.getCreatedAt(), callerRole, memberList);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
