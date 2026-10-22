package com.glitchbytez.splitwise.expense.split;

import java.util.UUID;

/**
 * A computed share of an expense.
 *
 * @param percentageBp the requested percentage for PERCENTAGE splits, otherwise {@code null}
 */
public record Share(UUID userId, long amountMinor, Integer percentageBp) {
}
