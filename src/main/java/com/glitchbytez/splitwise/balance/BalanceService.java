package com.glitchbytez.splitwise.balance;

import java.util.UUID;

import com.glitchbytez.splitwise.group.MemberBalances;
import org.springframework.stereotype.Service;

/**
 * Derives balances from expenses and settlements (spec 4.3).
 * <p>
 * Expenses and settlements do not exist yet, so every member's balance is zero. Phase 4 replaces this with the
 * real derivation; see {@code docs/DECISIONS.md} (D21).
 */
@Service
public class BalanceService implements MemberBalances {

    @Override
    public boolean isSettled(UUID groupId, UUID userId) {
        return true;
    }
}
