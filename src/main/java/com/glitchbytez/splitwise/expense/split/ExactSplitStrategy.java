package com.glitchbytez.splitwise.expense.split;

import java.util.List;

import com.glitchbytez.splitwise.common.money.Money;

/** Each participant owes exactly the amount given; the amounts must add up to the total. */
public final class ExactSplitStrategy implements SplitStrategy {

    static final ExactSplitStrategy INSTANCE = new ExactSplitStrategy();

    private ExactSplitStrategy() {
    }

    @Override
    public List<Share> split(long totalMinor, List<SplitInput> participants) {
        SplitStrategy.requireValidBasics(totalMinor, participants);
        long sum = 0;
        for (SplitInput participant : participants) {
            if (participant.percentageBp() != null) {
                throw new InvalidSplitException("An EXACT split takes amounts, not percentages");
            }
            if (participant.amountMinor() == null) {
                throw new InvalidSplitException("Participant " + participant.userId() + " needs an amount");
            }
            if (participant.amountMinor() < 0) {
                throw new InvalidSplitException("Participant " + participant.userId() + " has a negative amount");
            }
            sum = addOrFail(sum, participant.amountMinor());
        }
        if (sum != totalMinor) {
            throw new InvalidSplitException(
                    "The amounts add up to " + Money.ofMinor(sum).toDecimalString() + " but the total is " + Money.ofMinor(totalMinor).toDecimalString());
        }
        return participants.stream()
                .map(p -> new Share(p.userId(), p.amountMinor(), null))
                .toList();
    }

    private static long addOrFail(long sum, long amount) {
        try {
            return Math.addExact(sum, amount);
        } catch (ArithmeticException e) {
            throw new InvalidSplitException("The amounts are too large");
        }
    }
}
