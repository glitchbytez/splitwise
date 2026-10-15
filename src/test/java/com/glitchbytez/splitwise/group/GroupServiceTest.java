package com.glitchbytez.splitwise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.glitchbytez.splitwise.activity.ActivityService;
import com.glitchbytez.splitwise.activity.ActivityType;
import com.glitchbytez.splitwise.common.error.BadRequestException;
import com.glitchbytez.splitwise.common.error.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID CALLER = UUID.randomUUID();

    @Mock
    GroupRepository groups;
    @Mock
    GroupMemberRepository members;
    @Mock
    GroupAccessService access;
    @Mock
    ActivityService activity;

    GroupService service;

    @BeforeEach
    void setUp() {
        service = new GroupService(groups, members, access, activity, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void creatingAGroupMakesTheCallerItsOwnerAndLogsIt() {
        UUID groupId = UUID.randomUUID();
        when(groups.saveAndFlush(any(Group.class))).thenAnswer(inv -> withId(inv.getArgument(0), groupId));

        GroupResponse response = service.create(CALLER, new CreateGroupRequest("  Lisbon trip ", "   "));

        ArgumentCaptor<GroupMember> owner = ArgumentCaptor.forClass(GroupMember.class);
        verify(members).save(owner.capture());
        assertThat(owner.getValue().getUserId()).isEqualTo(CALLER);
        assertThat(owner.getValue().getRole()).isEqualTo(GroupRole.OWNER);
        assertThat(owner.getValue().getJoinedAt()).isEqualTo(NOW);
        assertThat(response.name()).isEqualTo("Lisbon trip");
        assertThat(response.description()).as("blank description is stored as null").isNull();
        assertThat(response.myRole()).isEqualTo(GroupRole.OWNER);
        verify(activity).record(groupId, CALLER, ActivityType.GROUP_CREATED, "GROUP", groupId, Map.of());
    }

    @Test
    void updateOnlyChangesTheFieldsThatWereSent() {
        UUID groupId = UUID.randomUUID();
        Group group = withId(Group.create("Old", "Keep me", CALLER), groupId);
        when(access.requireOwner(groupId, CALLER))
                .thenReturn(GroupMember.join(groupId, CALLER, GroupRole.OWNER, NOW));
        when(access.requireGroup(groupId)).thenReturn(group);

        GroupResponse response = service.update(groupId, CALLER, new UpdateGroupRequest(" New ", null));

        assertThat(response.name()).isEqualTo("New");
        assertThat(response.description()).isEqualTo("Keep me");
        verify(activity).record(groupId, CALLER, ActivityType.GROUP_UPDATED, "GROUP", groupId,
                Map.of("changed", List.of("name")));
    }

    @Test
    void anEmptyDescriptionClearsIt() {
        UUID groupId = UUID.randomUUID();
        Group group = withId(Group.create("Trip", "Old description", CALLER), groupId);
        when(access.requireOwner(groupId, CALLER))
                .thenReturn(GroupMember.join(groupId, CALLER, GroupRole.OWNER, NOW));
        when(access.requireGroup(groupId)).thenReturn(group);

        service.update(groupId, CALLER, new UpdateGroupRequest(null, ""));

        assertThat(group.getDescription()).isNull();
    }

    @Test
    void onlyTheOwnerCanUpdate() {
        UUID groupId = UUID.randomUUID();
        when(access.requireOwner(groupId, CALLER)).thenThrow(new ForbiddenException(GroupAccessService.OWNER_ONLY));

        assertThatThrownBy(() -> service.update(groupId, CALLER, new UpdateGroupRequest("x", null)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(activity);
    }

    @Test
    void deletingIsASoftDeleteByTheOwner() {
        UUID groupId = UUID.randomUUID();
        Group group = withId(Group.create("Trip", null, CALLER), groupId);
        when(access.requireGroup(groupId)).thenReturn(group);

        service.delete(groupId, CALLER);

        verify(access).requireOwner(groupId, CALLER);
        assertThat(group.getDeletedAt()).isEqualTo(NOW);
        verify(activity).record(eq(groupId), eq(CALLER), eq(ActivityType.GROUP_DELETED), eq("GROUP"), eq(groupId),
                anyMap());
    }

    @Test
    void listingRejectsUnsupportedSortProperties() {
        var pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThatThrownBy(() -> service.listForMember(CALLER, pageable))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'passwordHash'")
                .hasMessageContaining("createdAt, name");
        verifyNoInteractions(groups);
    }

    private static Group withId(Group group, UUID id) {
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }
}
