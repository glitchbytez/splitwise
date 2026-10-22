package com.glitchbytez.splitwise.expense.split;

import java.util.UUID;

/**
 * One participant as requested by the client. Which of {@code amountMinor} and {@code percentageBp} must be set
 * depends on the split type; the strategy validates that.
 *
 * @param amountMinor  exact share in minor units (EXACT only)
 * @param percentageBp percentage in basis points, 10000 = 100% (PERCENTAGE only)
 */
public record SplitInput(UUID userId, Long amountMinor, Integer percentageBp) {

    public static SplitInput equal(UUID userId) {
        return new SplitInput(userId, null, null);
    }

    public static SplitInput exact(UUID userId, long amountMinor) {
        return new SplitInput(userId, amountMinor, null);
    }

    public static SplitInput percentage(UUID userId, int percentageBp) {
        return new SplitInput(userId, null, percentageBp);
    }
}
