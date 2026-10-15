package com.glitchbytez.splitwise.group;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByIdAndDeletedAtIsNull(UUID id);

    /** The caller's active groups with their role and the number of active members, in one query. */
    @Query(value = """
            select g.id as id, g.name as name, g.description as description, m.role as myRole,
                   g.createdAt as createdAt,
                   (select count(other) from GroupMember other
                     where other.id.groupId = g.id and other.leftAt is null) as memberCount
            from ExpenseGroup g, GroupMember m
            where m.id.groupId = g.id and m.id.userId = :userId
              and m.leftAt is null and g.deletedAt is null
            """,
            countQuery = """
                    select count(g) from ExpenseGroup g, GroupMember m
                    where m.id.groupId = g.id and m.id.userId = :userId
                      and m.leftAt is null and g.deletedAt is null
                    """)
    Page<GroupSummaryView> findSummariesForMember(@Param("userId") UUID userId, Pageable pageable);

    interface GroupSummaryView {
        UUID getId();

        String getName();

        String getDescription();

        GroupRole getMyRole();

        Instant getCreatedAt();

        long getMemberCount();
    }
}
