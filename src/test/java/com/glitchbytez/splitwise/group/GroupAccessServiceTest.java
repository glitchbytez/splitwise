package com.glitchbytez.splitwise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.glitchbytez.splitwise.common.error.ForbiddenException;
import com.glitchbytez.splitwise.common.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupAccessServiceTest {

    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    GroupRepository groups;
    @Mock
    GroupMemberRepository members;
    @InjectMocks
    GroupAccessService access;

    @Test
    void anActiveMemberPasses() {
        givenGroupExists();
        GroupMember member = GroupMember.join(GROUP_ID, USER_ID, GroupRole.MEMBER, Instant.now());
        when(members.findActive(GROUP_ID, USER_ID)).thenReturn(Optional.of(member));

        assertThat(access.requireMember(GROUP_ID, USER_ID)).isSameAs(member);
    }

    @Test
    void aNonMemberIsForbidden() {
        givenGroupExists();
        when(members.findActive(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireMember(GROUP_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(GroupAccessService.NOT_A_MEMBER);
    }

    @Test
    void aMissingOrDeletedGroupIsNotFoundBeforeMembershipIsChecked() {
        when(groups.findByIdAndDeletedAtIsNull(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireMember(GROUP_ID, USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(GroupAccessService.GROUP_NOT_FOUND);
    }

    @Test
    void ownerOnlyActionsRejectPlainMembers() {
        givenGroupExists();
        when(members.findActive(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(GroupMember.join(GROUP_ID, USER_ID, GroupRole.MEMBER, Instant.now())));

        assertThatThrownBy(() -> access.requireOwner(GROUP_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(GroupAccessService.OWNER_ONLY);
    }

    @Test
    void ownerOnlyActionsAcceptTheOwner() {
        givenGroupExists();
        GroupMember owner = GroupMember.join(GROUP_ID, USER_ID, GroupRole.OWNER, Instant.now());
        when(members.findActive(GROUP_ID, USER_ID)).thenReturn(Optional.of(owner));

        assertThat(access.requireOwner(GROUP_ID, USER_ID)).isSameAs(owner);
    }

    private void givenGroupExists() {
        when(groups.findByIdAndDeletedAtIsNull(GROUP_ID))
                .thenReturn(Optional.of(Group.create("Trip", null, USER_ID)));
    }
}
