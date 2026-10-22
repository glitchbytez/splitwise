package com.glitchbytez.splitwise.expense.split;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code base = total / n}, {@code remainder = total % n}; the first {@code remainder} participants in
 * {@link SplitStrategy#USER_ORDER} get one extra minor unit. E.g. 10.00 / 3 = 3.34, 3.33, 3.33.
 */
public final class EqualSplitStrategy implements SplitStrategy {

    static final EqualSplitStrategy INSTANCE = new EqualSplitStrategy();

    private EqualSplitStrategy() {
    }

    @Override
    public List<Share> split(long totalMinor, List<SplitInput> participants) {
        SplitStrategy.requireValidBasics(totalMinor, participants);
        for (SplitInput participant : participants) {
            if (participant.amountMinor() != null || participant.percentageBp() != null) {
                throw new InvalidSplitException(
                        "An EQUAL split takes participants only; do not send amounts or percentages");
            }
        }

        int count = participants.size();
        long base = totalMinor / count;
        long remainder = totalMinor % count;

        List<UUID> byUserId = participants.stream().map(SplitInput::userId).sorted(USER_ORDER).toList();
        Map<UUID, Long> amounts = new HashMap<>();
        for (int i = 0; i < count; i++) {
            amounts.put(byUserId.get(i), base + (i < remainder ? 1 : 0));
        }
        return participants.stream()
                .map(p -> new Share(p.userId(), amounts.get(p.userId()), null))
                .toList();
    }
}
