package com.glitchbytez.splitwise.expense.split;

import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.A;
import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.B;
import static com.glitchbytez.splitwise.expense.split.SplitTestUsers.C;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PercentageSplitStrategyTest {

    private final SplitStrategy strategy = SplitStrategy.forType(SplitType.PERCENTAGE);

    @Test
    void splitsTheSpecExample() {
        // 90.00 at 50 / 30 / 20
        List<Share> shares = strategy.split(9000, List.of(
                SplitInput.percentage(A, 5000), SplitInput.percentage(B, 3000), SplitInput.percentage(C, 2000)));

        assertThat(shares).containsExactly(
                new Share(A, 4500, 5000), new Share(B, 2700, 3000), new Share(C, 1800, 2000));
    }

    @Test
    void givesLeftoverCentsToTheLargestFractionalRemainders() {
        // 10.00 at 33.33 / 33.33 / 33.34 -> exact 3.333, 3.333, 3.334 -> floors 3.33, 3.33, 3.33, one cent left.
        // Remainders: A .3, B .3, C .4 -> C gets the cent.
        List<Share> shares = strategy.split(1000, List.of(
                SplitInput.percentage(A, 3333), SplitInput.percentage(B, 3333), SplitInput.percentage(C, 3334)));

        assertThat(shares).extracting(Share::amountMinor).containsExactly(333L, 333L, 334L);
    }

    @Test
    void breaksEqualRemaindersByUserId() {
        // 0.01 at 50 / 50 -> exact 0.005 each, one cent left, equal remainders -> A (lower id) gets it
        List<Share> shares = strategy.split(1, List.of(SplitInput.percentage(B, 5000), SplitInput.percentage(A, 5000)));

        assertThat(shares).extracting(Share::userId).containsExactly(B, A);
        assertThat(shares).extracting(Share::amountMinor).containsExactly(0L, 1L);
    }

    @Test
    void largestRemainderBeatsUserIdOrder() {
        // 0.10 at 15 / 15 / 70 -> exact 1.5, 1.5, 7.0 cents -> floors 1, 1, 7 = 9, one cent left.
        // A and B tie on remainder .5; C has 0 -> A wins the tie, C gets nothing extra.
        List<Share> shares = strategy.split(10, List.of(
                SplitInput.percentage(C, 7000), SplitInput.percentage(B, 1500), SplitInput.percentage(A, 1500)));

        assertThat(shares).extracting(Share::amountMinor).containsExactly(7L, 1L, 2L);
    }

    @Test
    void keepsTheRequestedPercentageOnEachShare() {
        List<Share> shares = strategy.split(1000, List.of(SplitInput.percentage(A, 1234), SplitInput.percentage(B, 8766)));

        assertThat(shares).extracting(Share::percentageBp).containsExactly(1234, 8766);
    }

    @Test
    void allowsAZeroPercentParticipant() {
        List<Share> shares = strategy.split(1000, List.of(SplitInput.percentage(A, 10000), SplitInput.percentage(B, 0)));

        assertThat(shares).extracting(Share::amountMinor).containsExactly(1000L, 0L);
    }

    @Test
    void aSingleParticipantAtOneHundredPercentGetsEverything() {
        assertThat(strategy.split(999, List.of(SplitInput.percentage(A, 10000))))
                .containsExactly(new Share(A, 999, 10000));
    }

    @Test
    void handlesTheLargestAmountsThatDoNotOverflow() {
        long total = Long.MAX_VALUE / 10_000;
        List<Share> shares = strategy.split(total, List.of(
                SplitInput.percentage(A, 3333), SplitInput.percentage(B, 3333), SplitInput.percentage(C, 3334)));

        assertThat(shares.stream().mapToLong(Share::amountMinor).sum()).isEqualTo(total);
    }

    @Test
    void rejectsTotalsThatWouldOverflow() {
        assertThatThrownBy(() -> strategy.split(Long.MAX_VALUE, List.of(SplitInput.percentage(A, 10000))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("too large");
    }

    @ParameterizedTest
    @ValueSource(ints = {9999, 10001, 0, 20000})
    void rejectsPercentagesThatDoNotSumToExactlyOneHundred(int secondBp) {
        assertThatThrownBy(() -> strategy.split(1000,
                List.of(SplitInput.percentage(A, 0), SplitInput.percentage(B, Math.min(secondBp, 10000)),
                        SplitInput.percentage(C, Math.max(secondBp - 10000, 0)))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("must add up to exactly 100.00");
    }

    @Test
    void reportsTheActualSumInTheError() {
        assertThatThrownBy(() -> strategy.split(1000,
                List.of(SplitInput.percentage(A, 3333), SplitInput.percentage(B, 3333), SplitInput.percentage(C, 3333))))
                .hasMessage("The percentages add up to 99.99 but must add up to exactly 100.00");
    }

    @Test
    void rejectsNegativeOrAboveOneHundredPercentages() {
        assertThatThrownBy(() -> strategy.split(1000,
                List.of(SplitInput.percentage(A, 11000), SplitInput.percentage(B, -1000))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Percentages must be between 0 and 100");
    }

    @Test
    void rejectsMissingPercentagesAndAmounts() {
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.percentage(A, 10000), SplitInput.equal(B))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessage("Participant " + B + " needs a percentage");
        assertThatThrownBy(() -> strategy.split(1000, List.of(SplitInput.exact(A, 1000))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("not amounts");
    }

    @Test
    void rejectsDuplicateParticipants() {
        assertThatThrownBy(() -> strategy.split(1000,
                List.of(SplitInput.percentage(A, 5000), SplitInput.percentage(A, 5000))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("listed more than once");
    }
}
