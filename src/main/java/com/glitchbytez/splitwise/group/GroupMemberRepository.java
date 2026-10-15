package com.glitchbytez.splitwise.group;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    @Query("""
            select m from GroupMember m
            where m.id.groupId = :groupId and m.id.userId = :userId and m.leftAt is null
            """)
    Optional<GroupMember> findActive(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    /** Active members of a group with their (possibly anonymised) display name, oldest first. */
    @Query("""
            select m.id.userId as userId, u.displayName as displayName, m.role as role, m.joinedAt as joinedAt
            from GroupMember m, User u
            where u.id = m.id.userId and m.id.groupId = :groupId and m.leftAt is null
            order by m.joinedAt, m.id.userId
            """)
    List<MemberView> findActiveMembers(@Param("groupId") UUID groupId);

    interface MemberView {
        UUID getUserId();

        String getDisplayName();

        GroupRole getRole();

        Instant getJoinedAt();
    }
}
