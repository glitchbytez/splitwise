package com.glitchbytez.splitwise.expense;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = "shares")
    Optional<Expense> findByIdAndGroupIdAndDeletedAtIsNull(UUID id, UUID groupId);

    /** Total paid by a user across the group's non-deleted expenses, in minor units. */
    @Query("""
            select coalesce(sum(e.amountMinor), 0) from Expense e
            where e.groupId = :groupId and e.paidBy = :userId and e.deletedAt is null
            """)
    long sumPaidBy(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    /** Total of a user's shares across the group's non-deleted expenses, in minor units. */
    @Query("""
            select coalesce(sum(s.shareMinor), 0) from ExpenseShare s join s.expense e
            where e.groupId = :groupId and s.userId = :userId and e.deletedAt is null
            """)
    long sumOwedBy(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
