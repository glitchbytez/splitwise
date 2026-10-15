package com.glitchbytez.splitwise.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.glitchbytez.splitwise.activity.ActivityService;
import com.glitchbytez.splitwise.activity.ActivityType;
import com.glitchbytez.splitwise.common.error.ConflictException;
import com.glitchbytez.splitwise.common.error.ForbiddenException;
import com.glitchbytez.splitwise.common.error.NotFoundException;
import com.glitchbytez.splitwise.user.User;
import com.glitchbytez.splitwise.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Mock
    GroupMemberRepository members;
    @Mock
    UserRepository users;
    @Mock
    GroupAccessService access;
    @Mock
    MemberBalances balances;
    @Mock
    ActivityService activity;

    GroupMemberService service;

    GroupMember owner;
    GroupMember member;

    @BeforeEach
    void setUp() {
        service = new GroupMemberService(members, users, access, balances, activity, Clock.fixed(NOW, ZoneOffset.UTC));
        owner = GroupMember.join(GROUP_ID, OWNER_ID, GroupRole.OWNER, NOW.minusSeconds(3600));
        member = GroupMember.join(GROUP_ID, MEMBER_ID, GroupRole.MEMBER, NOW.minusSeconds(1800));
    }

    @Nested
    class Add {

        User bob;

        @BeforeEach
        void setUp() {
            bob = User.register("bob@example.com", "Bob", "hash");
            ReflectionTestUtils.setField(bob, "id", UUID.randomUUID());
        }

        @Test
        void addsARegisteredUserFoundByNormalisedEmail() {
            when(users.findByEmailAndDeletedAtIsNull("bob@example.com")).thenReturn(Optional.of(bob));
            when(members.findById(new GroupMemberId(GROUP_ID, bob.getId()))).thenReturn(Optional.empty());
            when(members.saveAndFlush(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

            MemberResponse response = service.add(GROUP_ID, MEMBER_ID, new AddMemberRequest(" Bob@Example.com "));

            verify(access).requireMember(GROUP_ID, MEMBER_ID);
            assertThat(response.userId()).isEqualTo(bob.getId());
            assertThat(response.displayName()).isEqualTo("Bob");
            assertThat(response.role()).isEqualTo(GroupRole.MEMBER);
            assertThat(response.joinedAt()).isEqualTo(NOW);
            verify(activity).record(GROUP_ID, MEMBER_ID, ActivityType.MEMBER_ADDED, "USER", bob.getId(),
                    Map.of());
        }

        @Test
        void rejectsAnUnknownEmail() {
            when(users.findByEmailAndDeletedAtIsNull("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.add(GROUP_ID, OWNER_ID, new AddMemberRequest("ghost@example.com")))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage(GroupMemberService.USER_NOT_FOUND);
            verifyNoInteractions(activity);
        }

        @Test
        void rejectsSomeoneWhoIsAlreadyAMember() {
            GroupMember existing = GroupMember.join(GROUP_ID, bob.getId(), GroupRole.MEMBER, NOW);
            when(users.findByEmailAndDeletedAtIsNull("bob@example.com")).thenReturn(Optional.of(bob));
            when(members.findById(existing.getId())).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.add(GROUP_ID, OWNER_ID, new AddMemberRequest("bob@example.com")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage(GroupMemberService.ALREADY_MEMBER);
        }

        @Test
        void reactivatesAFormerMemberAsAPlainMember() {
            GroupMember former = GroupMember.join(GROUP_ID, bob.getId(), GroupRole.MEMBER, NOW.minusSeconds(9999));
            former.leave(NOW.minusSeconds(100));
            when(users.findByEmailAndDeletedAtIsNull("bob@example.com")).thenReturn(Optional.of(bob));
            when(members.findById(former.getId())).thenReturn(Optional.of(former));

            MemberResponse response = service.add(GROUP_ID, OWNER_ID, new AddMemberRequest("bob@example.com"));

            assertThat(former.isActive()).isTrue();
            assertThat(former.getJoinedAt()).isEqualTo(NOW);
            assertThat(response.role()).isEqualTo(GroupRole.MEMBER);
            verify(members, never()).saveAndFlush(any());
        }

        @Test
        void turnsAConcurrentDuplicateAddIntoAConflict() {
            when(users.findByEmailAndDeletedAtIsNull("bob@example.com")).thenReturn(Optional.of(bob));
            when(members.findById(any())).thenReturn(Optional.empty());
            when(members.saveAndFlush(any(GroupMember.class))).thenThrow(new DataIntegrityViolationException("pk"));

            assertThatThrownBy(() -> service.add(GROUP_ID, OWNER_ID, new AddMemberRequest("bob@example.com")))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void requiresTheCallerToBeAMember() {
            when(access.requireMember(GROUP_ID, MEMBER_ID)).thenThrow(new ForbiddenException("no"));

            assertThatThrownBy(() -> service.add(GROUP_ID, MEMBER_ID, new AddMemberRequest("bob@example.com")))
                    .isInstanceOf(ForbiddenException.class);
            verifyNoInteractions(users);
        }
    }

    @Nested
    class Leave {

        @Test
        void aSettledMemberCanLeave() {
            when(access.requireMember(GROUP_ID, MEMBER_ID)).thenReturn(member);
            when(balances.isSettled(GROUP_ID, MEMBER_ID)).thenReturn(true);

            service.remove(GROUP_ID, MEMBER_ID, MEMBER_ID);

            assertThat(member.isActive()).isFalse();
            assertThat(member.getLeftAt()).isEqualTo(NOW);
            verify(activity).record(eq(GROUP_ID), eq(MEMBER_ID), eq(ActivityType.MEMBER_LEFT), eq("USER"),
                    eq(MEMBER_ID), anyMap());
        }

        @Test
        void aMemberWithABalanceCannotLeave() {
            when(access.requireMember(GROUP_ID, MEMBER_ID)).thenReturn(member);
            when(balances.isSettled(GROUP_ID, MEMBER_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.remove(GROUP_ID, MEMBER_ID, MEMBER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage(GroupMemberService.LEAVE_WITH_BALANCE);
            assertThat(member.isActive()).isTrue();
        }

        @Test
        void theOwnerCannotLeave() {
            when(access.requireMember(GROUP_ID, OWNER_ID)).thenReturn(owner);

            assertThatThrownBy(() -> service.remove(GROUP_ID, OWNER_ID, OWNER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage(GroupMemberService.OWNER_CANNOT_LEAVE);
            assertThat(owner.isActive()).isTrue();
        }
    }

    @Nested
    class Remove {

        @Test
        void theOwnerCanRemoveASettledMember() {
            when(access.requireMember(GROUP_ID, OWNER_ID)).thenReturn(owner);
            when(members.findActive(GROUP_ID, MEMBER_ID)).thenReturn(Optional.of(member));
            when(balances.isSettled(GROUP_ID, MEMBER_ID)).thenReturn(true);

            service.remove(GROUP_ID, OWNER_ID, MEMBER_ID);

            assertThat(member.isActive()).isFalse();
            verify(activity).record(eq(GROUP_ID), eq(OWNER_ID), eq(ActivityType.MEMBER_REMOVED), eq("USER"),
                    eq(MEMBER_ID), anyMap());
        }

        @Test
        void theOwnerCannotRemoveAMemberWithABalance() {
            when(access.requireMember(GROUP_ID, OWNER_ID)).thenReturn(owner);
            when(members.findActive(GROUP_ID, MEMBER_ID)).thenReturn(Optional.of(member));
            when(balances.isSettled(GROUP_ID, MEMBER_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.remove(GROUP_ID, OWNER_ID, MEMBER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage(GroupMemberService.REMOVE_WITH_BALANCE);
            assertThat(member.isActive()).isTrue();
        }

        @Test
        void aPlainMemberCannotRemoveOthers() {
            when(access.requireMember(GROUP_ID, MEMBER_ID)).thenReturn(member);

            assertThatThrownBy(() -> service.remove(GROUP_ID, MEMBER_ID, OWNER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage(GroupAccessService.OWNER_ONLY);
            assertThat(owner.isActive()).isTrue();
            verifyNoInteractions(balances);
        }

        @Test
        void removingSomeoneWhoIsNotAMemberIsNotFound() {
            UUID stranger = UUID.randomUUID();
            when(access.requireMember(GROUP_ID, OWNER_ID)).thenReturn(owner);
            when(members.findActive(GROUP_ID, stranger)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.remove(GROUP_ID, OWNER_ID, stranger))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage(GroupMemberService.MEMBER_NOT_FOUND);
        }
    }
}
