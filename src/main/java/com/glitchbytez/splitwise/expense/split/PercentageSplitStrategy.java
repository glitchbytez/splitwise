package com.glitchbytez.splitwise.expense.split;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Percentages are integer basis points (3333 = 33.33%) and must sum to exactly 10000.
 * <p>
 * Each share is first floored ({@code total * bp / 10000}); the cents lost to flooring are then handed out one
 * at a time to the participants with the largest fractional remainders (largest remainder method), ties broken
 * by {@link SplitStrategy#USER_ORDER}. E.g. 100.00 at 33.33/33.33/33.34 = 33.33, 33.33, 33.34.
 */
public final class PercentageSplitStrategy implements SplitStrategy {

    static final PercentageSplitStrategy INSTANCE = new PercentageSplitStrategy();
    static final int FULL_BP = 10_000;

    private PercentageSplitStrategy() {
    }

    @Override
    public List<Share> split(long totalMinor, List<SplitInput> participants) {
        SplitStrategy.requireValidBasics(totalMinor, participants);
        int bpSum = 0;
        for (SplitInput participant : participants) {
            if (participant.amountMinor() != null) {
                throw new InvalidSplitException("A PERCENTAGE split takes percentages, not amounts");
            }
            Integer bp = participant.percentageBp();
            if (bp == null) {
                throw new InvalidSplitException("Participant " + participant.userId() + " needs a percentage");
            }
            if (bp < 0 || bp > FULL_BP) {
                throw new InvalidSplitException("Percentages must be between 0 and 100");
            }
            bpSum += bp;
        }
        if (bpSum != FULL_BP) {
            throw new InvalidSplitException("The percentages add up to " + BasisPoints.toPercentage(bpSum)
                    + " but must add up to exactly 100.00");
        }

        Map<UUID, Allocation> allocations = new LinkedHashMap<>();
        long allocated = 0;
        for (SplitInput participant : participants) {
            long exactTimesFullBp = multiplyOrFail(totalMinor, participant.percentageBp());
            long floor = exactTimesFullBp / FULL_BP;
            allocations.put(participant.userId(), new Allocation(participant.userId(), floor, exactTimesFullBp % FULL_BP));
            allocated += floor;
        }

        Set<UUID> getsExtraCent = largestRemainders(totalMinor - allocated, allocations.values());
        return participants.stream()
                .map(p -> new Share(p.userId(),
                        allocations.get(p.userId()).floor() + (getsExtraCent.contains(p.userId()) ? 1 : 0),
                        p.percentageBp()))
                .toList();
    }

    /**
     * The {@code leftover} participants with the largest remainders. {@code leftover} is always smaller than the
     * number of participants, because flooring loses less than one cent per participant.
     */
    private static Set<UUID> largestRemainders(long leftover, Collection<Allocation> allocations) {
        return allocations.stream()
                .sorted(Comparator.comparingLong(Allocation::remainder).reversed()
                        .thenComparing(Allocation::userId, USER_ORDER))
                .limit(leftover)
                .map(Allocation::userId)
                .collect(Collectors.toSet());
    }

    private static long multiplyOrFail(long totalMinor, int bp) {
        try {
            return Math.multiplyExact(totalMinor, (long) bp);
        } catch (ArithmeticException e) {
            throw new InvalidSplitException("The total is too large for a percentage split");
        }
    }

    private record Allocation(UUID userId, long floor, long remainder) {
    }
}
