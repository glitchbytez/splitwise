package com.glitchbytez.splitwise.balance;

import java.util.UUID;

import com.glitchbytez.splitwise.expense.ExpenseRepository;
import com.glitchbytez.splitwise.group.MemberBalances;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives balances from expenses (spec 4.3): {@code net = paid - owed}, ignoring soft-deleted expenses.
 * Balances are never stored.
 * <p>
 * Settlements arrive in Phase 4 and will be added to this derivation, together with per-group and per-user
 * balance views (see {@code docs/DECISIONS.md}, D21).
 */
@Service
public class BalanceService implements MemberBalances {

    private final ExpenseRepository expenses;

    public BalanceService(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    /** A member's net balance in a group in minor units: positive = is owed money, negative = owes money. */
    @Transactional(readOnly = true)
    public long netBalanceMinor(UUID groupId, UUID userId) {
        return expenses.sumPaidBy(groupId, userId) - expenses.sumOwedBy(groupId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSettled(UUID groupId, UUID userId) {
        return netBalanceMinor(groupId, userId) == 0;
    }
}
